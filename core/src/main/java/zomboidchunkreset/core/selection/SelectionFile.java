package zomboidchunkreset.core.selection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import zomboidchunkreset.core.json.MiniJson;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.reset.ResetMode;

/**
 * Saves a selection to a small JSON file, to reuse protected areas from one save to another.
 *
 * <pre>
 * {
 *   "format": "zomboid-chunk-reset-selection",
 *   "version": 1,
 *   "mode": "KEEP_SELECTED",
 *   "chunks": [
 *     { "x1": 1500, "y1": 300, "x2": 1511, "y2": 308, "inGame": "X 12000-12095, Y 2400-2471" }
 *   ]
 * }
 * </pre>
 *
 * {@code chunks} holds inclusive rectangles in chunk coordinates ({@code inGame} is informative and
 * ignored when reading). A hand-written file may also (or instead) use {@code squares}: inclusive
 * rectangles in in-game coordinates, which select every chunk they touch. {@code mode} is optional.
 */
public final class SelectionFile {

    /** Value of the {@code format} property. */
    public static final String FORMAT = "zomboid-chunk-reset-selection";

    /** Current version of the format. */
    public static final int VERSION = 1;

    /** Coordinates beyond this (in squares) are rejected as nonsense. */
    private static final long MAX_ABS_SQUARE = 10_000_000L;

    private SelectionFile() {
    }

    /** What a selection file contains. */
    public record Content(ChunkSelection selection, Optional<ResetMode> mode) {
    }

    /** Thrown when a file is not a valid selection file. */
    public static final class InvalidSelectionFileException extends IOException {
        private static final long serialVersionUID = 1L;

        public InvalidSelectionFileException(String message) {
            super(message);
        }
    }

    /** Writes a selection (and optionally the mode it is meant for). */
    public static void write(Path file, ChunkSelection selection, ResetMode mode) throws IOException {
        Files.writeString(file, toJson(selection, mode), StandardCharsets.UTF_8);
    }

    /** The JSON text of a selection file. */
    public static String toJson(ChunkSelection selection, ResetMode mode) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        if (mode != null) {
            root.put("mode", mode.name());
        }
        List<Object> rects = new ArrayList<>();
        for (ChunkBounds b : rectangles(selection)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("x1", b.minX());
            r.put("y1", b.minY());
            r.put("x2", b.maxX());
            r.put("y2", b.maxY());
            ChunkPos first = new ChunkPos(b.minX(), b.minY());
            ChunkPos last = new ChunkPos(b.maxX(), b.maxY());
            r.put("inGame", "X " + first.minSquareX() + "-" + last.maxSquareX() + ", Y " + first.minSquareY() + "-"
                    + last.maxSquareY());
            rects.add(r);
        }
        root.put("chunks", rects);
        return MiniJson.write(root);
    }

    /** Reads a selection file. */
    public static Content read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Parses the JSON text of a selection file. */
    public static Content parse(String json) throws InvalidSelectionFileException {
        Object root;
        try {
            root = MiniJson.parse(json.startsWith("﻿") ? json.substring(1) : json);
        } catch (MiniJson.JsonException e) {
            throw new InvalidSelectionFileException("Not valid JSON: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> map) || !FORMAT.equals(map.get("format"))) {
            throw new InvalidSelectionFileException("Not a selection file (\"format\" must be \"" + FORMAT + "\")");
        }
        Object version = map.get("version");
        if (!(version instanceof Long v) || v < 1 || v > VERSION) {
            throw new InvalidSelectionFileException("Unsupported version: " + version);
        }
        Optional<ResetMode> mode = Optional.empty();
        if (map.get("mode") != null) {
            try {
                mode = Optional.of(ResetMode.valueOf(String.valueOf(map.get("mode"))));
            } catch (IllegalArgumentException e) {
                throw new InvalidSelectionFileException("Unknown mode: " + map.get("mode"));
            }
        }
        ChunkSelection selection = new ChunkSelection();
        for (Map<?, ?> r : rectangleList(map, "chunks")) {
            selection.addRect(ChunkBounds.of(new ChunkPos(coordinate(r, "x1", 1), coordinate(r, "y1", 1)),
                    new ChunkPos(coordinate(r, "x2", 1), coordinate(r, "y2", 1))));
        }
        for (Map<?, ?> r : rectangleList(map, "squares")) {
            selection.addRect(ChunkBounds.of(ChunkPos.ofSquare(coordinate(r, "x1", 8), coordinate(r, "y1", 8)),
                    ChunkPos.ofSquare(coordinate(r, "x2", 8), coordinate(r, "y2", 8))));
        }
        return new Content(selection, mode);
    }

    private static List<Map<?, ?>> rectangleList(Map<?, ?> root, String name) throws InvalidSelectionFileException {
        Object value = root.get(name);
        List<Map<?, ?>> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof List<?> list)) {
            throw new InvalidSelectionFileException("\"" + name + "\" must be a list");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new InvalidSelectionFileException("\"" + name + "\" must contain objects");
            }
            result.add(m);
        }
        return result;
    }

    /** Reads an integer field; {@code squaresPerUnit} scales the sanity limit (1 for chunks). */
    private static int coordinate(Map<?, ?> r, String field, int squaresPerUnit) throws InvalidSelectionFileException {
        Object v = r.get(field);
        if (!(v instanceof Long n)) {
            throw new InvalidSelectionFileException("Missing or non-integer \"" + field + "\" in " + r);
        }
        long limit = squaresPerUnit == 1 ? MAX_ABS_SQUARE / 8 : MAX_ABS_SQUARE;
        if (Math.abs(n) > limit) {
            throw new InvalidSelectionFileException("Coordinate out of range: " + field + " = " + n);
        }
        return (int) (long) n;
    }

    /**
     * Describes a selection as few rectangles: runs of consecutive chunks on each row, merged with the
     * identical runs of the rows below. A rectangular selection gives exactly one rectangle.
     */
    public static List<ChunkBounds> rectangles(ChunkSelection selection) {
        SortedMap<Integer, List<Integer>> rows = new TreeMap<>();
        selection.forEach(p -> rows.computeIfAbsent(p.y(), k -> new ArrayList<>()).add(p.x()));
        List<ChunkBounds> done = new ArrayList<>();
        // Open rectangles by run (x1, x2) -> [y1, lastY]
        Map<Long, int[]> open = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> row : rows.entrySet()) {
            int y = row.getKey();
            List<Integer> xs = row.getValue();
            xs.sort(null);
            Map<Long, int[]> next = new LinkedHashMap<>();
            int i = 0;
            while (i < xs.size()) {
                int x1 = xs.get(i);
                int x2 = x1;
                while (i + 1 < xs.size() && xs.get(i + 1) == x2 + 1) {
                    x2 = xs.get(++i);
                }
                i++;
                long key = new ChunkPos(x1, x2).toKey();
                int[] rect = open.remove(key);
                if (rect != null && rect[1] == y - 1) {
                    rect[1] = y;
                    next.put(key, rect);
                } else {
                    if (rect != null) {
                        done.add(bounds(key, rect));
                    }
                    next.put(key, new int[] {y, y});
                }
            }
            for (Map.Entry<Long, int[]> e : open.entrySet()) {
                done.add(bounds(e.getKey(), e.getValue()));
            }
            open = next;
        }
        for (Map.Entry<Long, int[]> e : open.entrySet()) {
            done.add(bounds(e.getKey(), e.getValue()));
        }
        done.sort((a, b) -> a.minY() != b.minY() ? Integer.compare(a.minY(), b.minY())
                : Integer.compare(a.minX(), b.minX()));
        return done;
    }

    private static ChunkBounds bounds(long runKey, int[] ys) {
        ChunkPos run = ChunkPos.fromKey(runKey);
        return new ChunkBounds(run.x(), ys[0], run.y(), ys[1]);
    }
}
