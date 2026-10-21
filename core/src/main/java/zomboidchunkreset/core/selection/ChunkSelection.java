package zomboidchunkreset.core.selection;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;

/**
 * A mutable set of chunk positions. Positions do not need to have a chunk file: a selection may
 * cover areas that were never generated. Not thread-safe.
 *
 * <p>Stored as one 32 x 32 bitmap per cell, so that large rectangles (a whole map is a few hundred
 * thousand chunks) stay small in memory and fast to edit.
 */
public final class ChunkSelection {

    private static final int SIDE = WorldGeometry.CELL_SIZE_IN_CHUNKS; // 32
    private static final int WORDS = SIDE * SIDE / Long.SIZE; // 16 longs = 1,024 bits

    /** Cell key -> 1,024-bit bitmap; bit index = localY * 32 + localX. Never holds an empty bitmap. */
    private final Map<Long, long[]> cells = new HashMap<>();
    private int size;

    /** Creates an empty selection. */
    public ChunkSelection() {
    }

    /** Creates a selection holding the given positions. */
    public static ChunkSelection of(Iterable<ChunkPos> positions) {
        ChunkSelection s = new ChunkSelection();
        for (ChunkPos p : positions) {
            s.add(p);
        }
        return s;
    }

    /** Creates a selection covering an inclusive rectangle. */
    public static ChunkSelection ofRect(ChunkBounds bounds) {
        ChunkSelection s = new ChunkSelection();
        s.addRect(bounds);
        return s;
    }

    private static long cellKey(int cellX, int cellY) {
        return new ChunkPos(cellX, cellY).toKey();
    }

    private static int bit(int x, int y) {
        return Math.floorMod(y, SIDE) * SIDE + Math.floorMod(x, SIDE);
    }

    public boolean contains(ChunkPos pos) {
        return contains(pos.x(), pos.y());
    }

    /** Same as {@link #contains(ChunkPos)} without allocating a position. */
    public boolean contains(int x, int y) {
        long[] bits = cells.get(cellKey(WorldGeometry.chunkToCell(x), WorldGeometry.chunkToCell(y)));
        if (bits == null) {
            return false;
        }
        int b = bit(x, y);
        return (bits[b >>> 6] & (1L << (b & 63))) != 0;
    }

    /** @return true if the position was not selected yet */
    public boolean add(ChunkPos pos) {
        long key = cellKey(WorldGeometry.chunkToCell(pos.x()), WorldGeometry.chunkToCell(pos.y()));
        long[] bits = cells.computeIfAbsent(key, k -> new long[WORDS]);
        int b = bit(pos.x(), pos.y());
        long mask = 1L << (b & 63);
        if ((bits[b >>> 6] & mask) != 0) {
            return false;
        }
        bits[b >>> 6] |= mask;
        size++;
        return true;
    }

    /** @return true if the position was selected */
    public boolean remove(ChunkPos pos) {
        long key = cellKey(WorldGeometry.chunkToCell(pos.x()), WorldGeometry.chunkToCell(pos.y()));
        long[] bits = cells.get(key);
        if (bits == null) {
            return false;
        }
        int b = bit(pos.x(), pos.y());
        long mask = 1L << (b & 63);
        if ((bits[b >>> 6] & mask) == 0) {
            return false;
        }
        bits[b >>> 6] &= ~mask;
        size--;
        if (isZero(bits)) {
            cells.remove(key);
        }
        return true;
    }

    private enum Op { SET, CLEAR, FLIP }

    /** Selects every position of the rectangle. */
    public void addRect(ChunkBounds bounds) {
        applyRect(bounds, Op.SET);
    }

    /** Deselects every position of the rectangle. */
    public void removeRect(ChunkBounds bounds) {
        applyRect(bounds, Op.CLEAR);
    }

    /** Toggles every position inside the rectangle; positions outside it are not changed. */
    public void invertWithin(ChunkBounds bounds) {
        applyRect(bounds, Op.FLIP);
    }

    private void applyRect(ChunkBounds b, Op op) {
        int minCellX = WorldGeometry.chunkToCell(b.minX());
        int maxCellX = WorldGeometry.chunkToCell(b.maxX());
        int minCellY = WorldGeometry.chunkToCell(b.minY());
        int maxCellY = WorldGeometry.chunkToCell(b.maxY());
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            int x0 = Math.max(b.minX(), cx * SIDE) - cx * SIDE;
            int x1 = Math.min(b.maxX(), cx * SIDE + SIDE - 1) - cx * SIDE;
            long rowMask = (x1 - x0 == SIDE - 1 ? 0xFFFF_FFFFL : ((1L << (x1 - x0 + 1)) - 1)) << x0;
            for (int cy = minCellY; cy <= maxCellY; cy++) {
                int y0 = Math.max(b.minY(), cy * SIDE) - cy * SIDE;
                int y1 = Math.min(b.maxY(), cy * SIDE + SIDE - 1) - cy * SIDE;
                long key = cellKey(cx, cy);
                long[] bits = cells.get(key);
                if (bits == null) {
                    if (op == Op.CLEAR) {
                        continue;
                    }
                    bits = new long[WORDS];
                    cells.put(key, bits);
                }
                for (int ly = y0; ly <= y1; ly++) {
                    int word = ly >>> 1;
                    long mask = rowMask << ((ly & 1) * SIDE);
                    long before = bits[word];
                    long after = switch (op) {
                        case SET -> before | mask;
                        case CLEAR -> before & ~mask;
                        case FLIP -> before ^ mask;
                    };
                    bits[word] = after;
                    size += Long.bitCount(after) - Long.bitCount(before);
                }
                if (isZero(bits)) {
                    cells.remove(key);
                }
            }
        }
    }

    private static boolean isZero(long[] bits) {
        for (long w : bits) {
            if (w != 0) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        cells.clear();
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Calls {@code action} for every selected position, in no particular order. */
    public void forEach(Consumer<ChunkPos> action) {
        for (Map.Entry<Long, long[]> e : cells.entrySet()) {
            ChunkPos cell = ChunkPos.fromKey(e.getKey());
            long[] bits = e.getValue();
            for (int w = 0; w < WORDS; w++) {
                long word = bits[w];
                while (word != 0) {
                    int b = (w << 6) + Long.numberOfTrailingZeros(word);
                    word &= word - 1;
                    action.accept(new ChunkPos(cell.x() * SIDE + (b % SIDE), cell.y() * SIDE + (b / SIDE)));
                }
            }
        }
    }

    /** A sorted copy of the selected positions. */
    public SortedSet<ChunkPos> positions() {
        SortedSet<ChunkPos> result = new TreeSet<>();
        forEach(result::add);
        return result;
    }

    /** Number of selected positions in each cell (at most 1,024 per cell). */
    public Map<CellPos, Integer> countByCell() {
        Map<CellPos, Integer> counts = new HashMap<>();
        for (Map.Entry<Long, long[]> e : cells.entrySet()) {
            ChunkPos cell = ChunkPos.fromKey(e.getKey());
            int n = 0;
            for (long w : e.getValue()) {
                n += Long.bitCount(w);
            }
            counts.put(new CellPos(cell.x(), cell.y()), n);
        }
        return counts;
    }

    /** Smallest rectangle containing the selection, empty if nothing is selected. */
    public Optional<ChunkBounds> bounds() {
        ChunkBounds[] result = {null};
        forEach(p -> result[0] = result[0] == null ? ChunkBounds.of(p, p) : result[0].including(p));
        return Optional.ofNullable(result[0]);
    }

    /** An independent copy of this selection. */
    public ChunkSelection copy() {
        ChunkSelection c = new ChunkSelection();
        for (Map.Entry<Long, long[]> e : cells.entrySet()) {
            c.cells.put(e.getKey(), e.getValue().clone());
        }
        c.size = size;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChunkSelection other) || other.size != size || !other.cells.keySet().equals(cells.keySet())) {
            return false;
        }
        for (Map.Entry<Long, long[]> e : cells.entrySet()) {
            if (!Arrays.equals(e.getValue(), other.cells.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = size;
        for (Map.Entry<Long, long[]> e : cells.entrySet()) {
            h += Long.hashCode(e.getKey()) ^ Arrays.hashCode(e.getValue());
        }
        return h;
    }
}
