package zomboidchunkreset.core.terrain;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sprite id → {@link TileClass} lookup used to colour the map.
 *
 * <p>The table shipped with the application ({@value #RESOURCE}) is derived once, by a development
 * tool, from the vanilla game's {@code .tiles} files. It contains only numbers (sprite ids and class
 * codes): no tile names and no game file is redistributed.
 *
 * <p>File format (gzip-compressed, big-endian): {@code "ZCRT"}, int format version (1), int run count,
 * then per run of consecutive ids: int first id, unsigned short count, {@code count} class codes (one
 * byte each).
 */
public final class TileTable {

    /** Classpath location of the bundled table. */
    public static final String RESOURCE = "/zomboidchunkreset/core/terrain/tile-table.bin";

    private static final int MAGIC = 0x5A435254; // "ZCRT"
    private static final int FORMAT_VERSION = 1;
    /** Ids beyond this are not plausible for vanilla tiles; protects against corrupt data. */
    private static final int MAX_ID = 1 << 24;

    private static volatile TileTable bundled;

    private final byte[] codes;

    private TileTable(byte[] codes) {
        this.codes = codes;
    }

    /** The table bundled with the application (loaded once). */
    public static TileTable bundled() {
        TileTable t = bundled;
        if (t == null) {
            synchronized (TileTable.class) {
                t = bundled;
                if (t == null) {
                    try (InputStream in = TileTable.class.getResourceAsStream(RESOURCE)) {
                        if (in == null) {
                            throw new IOException("missing resource " + RESOURCE);
                        }
                        t = read(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    bundled = t;
                }
            }
        }
        return t;
    }

    /** Builds a table from an id → class map. */
    public static TileTable of(Map<Integer, TileClass> classes) {
        int max = classes.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        if (max >= MAX_ID) {
            throw new IllegalArgumentException("sprite id too large: " + max);
        }
        byte[] codes = new byte[max + 1];
        classes.forEach((id, c) -> {
            if (id < 0) {
                throw new IllegalArgumentException("negative sprite id");
            }
            codes[id] = (byte) c.code();
        });
        return new TileTable(codes);
    }

    /** Class of a sprite id; {@link TileClass#UNKNOWN} when the id is not in the table. */
    public TileClass classOf(int spriteId) {
        return TileClass.ofCode(codeOf(spriteId));
    }

    /** Raw class code of a sprite id (0 when unknown). */
    public int codeOf(int spriteId) {
        return spriteId >= 0 && spriteId < codes.length ? codes[spriteId] : 0;
    }

    /** Number of known sprite ids. */
    public int size() {
        int n = 0;
        for (byte c : codes) {
            if (c != 0) {
                n++;
            }
        }
        return n;
    }

    /** Reads a table in the file format described above. */
    public static TileTable read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
        if (in.readInt() != MAGIC) {
            throw new IOException("not a tile table");
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IOException("unsupported tile table version " + version);
        }
        int runs = in.readInt();
        if (runs < 0) {
            throw new IOException("corrupt tile table");
        }
        int[] firsts = new int[runs];
        byte[][] data = new byte[runs][];
        int max = -1;
        for (int r = 0; r < runs; r++) {
            int first = in.readInt();
            int count = in.readUnsignedShort();
            if (first < 0 || (long) first + count > MAX_ID) {
                throw new IOException("corrupt tile table");
            }
            data[r] = new byte[count];
            in.readFully(data[r]);
            firsts[r] = first;
            max = Math.max(max, first + count - 1);
        }
        if (in.read() != -1) {
            throw new IOException("trailing data in tile table");
        }
        byte[] codes = new byte[max + 1];
        for (int r = 0; r < runs; r++) {
            System.arraycopy(data[r], 0, codes, firsts[r], data[r].length);
        }
        return new TileTable(codes);
    }

    /** Serialises the table (runs of consecutive known ids). */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);
            int runs = 0;
            int id = 0;
            while (id < codes.length) {
                if (codes[id] == 0) {
                    id++;
                    continue;
                }
                int end = id;
                while (end < codes.length && codes[end] != 0 && end - id < 0xFFFF) {
                    end++;
                }
                out.writeInt(id);
                out.writeShort(end - id);
                out.write(codes, id, end - id);
                runs++;
                id = end;
            }
            out.flush();
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            try (DataOutputStream gz = new DataOutputStream(new GZIPOutputStream(file))) {
                gz.writeInt(MAGIC);
                gz.writeInt(FORMAT_VERSION);
                gz.writeInt(runs);
                body.writeTo(gz);
            }
            return file.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TileTable other)) {
            return false;
        }
        int n = Math.max(codes.length, other.codes.length);
        for (int i = 0; i < n; i++) {
            if (codeOf(i) != other.codeOf(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int end = codes.length;
        while (end > 0 && codes[end - 1] == 0) {
            end--;
        }
        return Arrays.hashCode(Arrays.copyOf(codes, end));
    }
}
