package zomboidchunkreset.core.terrain;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader for the game's tile definition files ({@code media/*.tiles}). Only used by the
 * development tool that builds the tile table; the application itself never reads game files.
 *
 * <p>Verified layout (every byte of the seven vanilla Build 42 files is accounted for):
 * <pre>
 * "tdef"                     4 bytes magic
 * int32 LE                   version (1)
 * int32 LE                   number of tilesheets
 * per tilesheet:
 *   name '\n', image name '\n'
 *   int32 LE columns, rows, tilesheet number, number of tiles
 *   per tile: int32 LE number of properties, then per property: key '\n', value '\n'
 * </pre>
 */
public final class TileDefinitions {

    /** One tilesheet: {@code tiles.get(i)} holds the properties of tile {@code i}. */
    public record Sheet(String name, String imageName, int columns, int rows, int number,
                        List<Map<String, String>> tiles) {
    }

    private TileDefinitions() {
    }

    /** Reads a {@code .tiles} file. */
    public static List<Sheet> read(Path file) throws IOException {
        try {
            return parse(Files.readAllBytes(file));
        } catch (IllegalArgumentException e) {
            throw new IOException(file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses the content of a {@code .tiles} file.
     *
     * @throws IllegalArgumentException if the content does not follow the format
     */
    public static List<Sheet> parse(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (data.length < 12 || data[0] != 't' || data[1] != 'd' || data[2] != 'e' || data[3] != 'f') {
                throw new IllegalArgumentException("not a tile definition file");
            }
            b.position(4);
            b.getInt(); // version
            int count = count(b.getInt());
            List<Sheet> sheets = new ArrayList<>(count);
            for (int s = 0; s < count; s++) {
                String name = line(b);
                String image = line(b);
                int columns = b.getInt();
                int rows = b.getInt();
                int number = b.getInt();
                int tileCount = count(b.getInt());
                List<Map<String, String>> tiles = new ArrayList<>(tileCount);
                for (int t = 0; t < tileCount; t++) {
                    int propertyCount = count(b.getInt());
                    Map<String, String> props = new LinkedHashMap<>();
                    for (int p = 0; p < propertyCount; p++) {
                        String key = line(b);
                        props.put(key, line(b));
                    }
                    tiles.add(Collections.unmodifiableMap(props));
                }
                sheets.add(new Sheet(name, image, columns, rows, number, List.copyOf(tiles)));
            }
            if (b.hasRemaining()) {
                throw new IllegalArgumentException(b.remaining() + " unexpected trailing bytes");
            }
            return List.copyOf(sheets);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IllegalArgumentException("truncated file", e);
        }
    }

    private static int count(int value) {
        if (value < 0 || value > 1_000_000) {
            throw new IllegalArgumentException("implausible count " + value);
        }
        return value;
    }

    private static String line(ByteBuffer b) {
        int start = b.position();
        int end = start;
        while (true) {
            if (end >= b.limit()) {
                throw new java.nio.BufferUnderflowException();
            }
            if (b.get(end) == '\n') {
                break;
            }
            end++;
        }
        String text = new String(b.array(), start, end - start, StandardCharsets.UTF_8);
        b.position(end + 1);
        return text;
    }
}
