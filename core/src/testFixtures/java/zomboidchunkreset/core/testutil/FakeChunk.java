package zomboidchunkreset.core.testutil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Writes synthetic chunk files following the Build 42 (world version 249) content layout, for
 * tests of the content decoder. Only invented values; no game data.
 */
public final class FakeChunk {

    /** Writes one serialised object (object flags byte excluded). */
    @FunctionalInterface
    public interface ObjectWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private final TreeMap<Integer, TreeMap<Integer, List<ObjectWriter>>> squares = new TreeMap<>();
    private final TreeMap<Integer, List<ObjectWriter>> bodies = new TreeMap<>();
    private int worldVersion = FakeSave.WORLD_VERSION;
    private int vehicles;
    private int trailingBytes;

    /** Adds a plain object (class 0) with the given sprite to a square. */
    public FakeChunk object(int x, int y, int level, int spriteId) {
        return add(x, y, level, out -> {
            out.writeByte(1);
            out.writeByte(0);
            baseObject(out, spriteId, 0);
        });
    }

    /** Adds plain objects on the ground level of a square. */
    public FakeChunk ground(int x, int y, int... spriteIds) {
        for (int id : spriteIds) {
            object(x, y, 0, id);
        }
        return this;
    }

    /** Adds any object writer to a square. */
    public FakeChunk add(int x, int y, int level, ObjectWriter writer) {
        squares.computeIfAbsent(x * 8 + y, k -> new TreeMap<>()).computeIfAbsent(level, k -> new ArrayList<>())
                .add(writer);
        return this;
    }

    /** Adds a door (class 17). */
    public FakeChunk door(int x, int y, int spriteId) {
        return add(x, y, 0, out -> {
            out.writeByte(1);
            out.writeByte(17);
            baseObject(out, spriteId, 0);
            out.write(new byte[3]);
            for (int i = 0; i < 5; i++) {
                out.writeInt(spriteId);
            }
            out.write(new byte[2]);
        });
    }

    /** Adds a tree (class 28). */
    public FakeChunk tree(int x, int y, int spriteId) {
        return add(x, y, 0, out -> {
            out.writeByte(1);
            out.writeByte(28);
            baseObject(out, spriteId, 0);
            out.writeByte(3);
            out.writeByte(0);
        });
    }

    /** Adds a furniture object holding a container with {@code items} items, a table and an entity. */
    public FakeChunk container(int x, int y, int spriteId, int items) {
        return add(x, y, 0, out -> {
            out.writeByte(1);
            out.writeByte(0);
            out.writeInt(spriteId);
            out.writeByte(64 | 8 | 4); // extra data, custom colour, sprite name
            out.writeByte(8);          // sprite name given as a string
            utf(out, "invented_name");
            out.write(new byte[] {1, 2, 3});
            out.writeShort(1 | 2 | 4 | 4096); // wall blood, containers, table, entity
            out.writeByte(1);
            out.write(new byte[8]);
            out.writeByte(1);
            utf(out, "crate");
            out.writeByte(1);
            out.writeShort(items);
            for (int i = 0; i < items; i++) {
                out.writeInt(i == 0 ? 3 : 1); // identical items
                byte[] item = new byte[5 + i];
                out.writeInt(item.length);
                out.write(item);
                if (i == 0) {
                    out.writeInt(11);
                    out.writeInt(12);
                }
            }
            out.writeByte(0);
            out.writeInt(50);
            out.writeInt(2);               // table: two entries
            out.writeByte(0);
            utf(out, "k");
            out.writeByte(1);
            out.writeDouble(1.5);
            out.writeByte(0);
            utf(out, "nested");
            out.writeByte(2);
            out.writeInt(1);
            out.writeByte(3);
            out.writeByte(1);
            out.writeByte(0);
            utf(out, "v");
            out.writeByte(1);              // entity: one component
            out.writeInt(6);
            out.writeShort(3);
            out.writeInt(0);
        });
    }

    /** Adds a dead body lying on a square (stored in the square's extra data). */
    public FakeChunk deadBody(int x, int y) {
        bodies.computeIfAbsent(x * 8 + y, k -> new ArrayList<>()).add(out -> {
            out.writeByte(1);
            out.writeByte(11);
            out.write(new byte[24]);
            out.writeByte(0);    // no table
            out.writeByte(1);    // female
            out.writeByte(1);    // was zombie
            out.writeByte(0);    // not an animal
            out.write(new byte[3]);
            out.writeByte(0);
            out.writeInt(-1);
            out.writeByte(1);    // survivor description
            out.writeInt(7);
            utf(out, "Ann");
            utf(out, "Example");
            utf(out, "Body");
            out.writeInt(1);
            utf(out, "unemployed");
            out.writeInt(0);
            out.writeInt(1);
            utf(out, "Fitness");
            out.writeInt(2);
            utf(out, "voice");
            out.write(new byte[8]);
            out.writeByte(0);    // human visual
            out.writeByte(4 | 16);
            out.write(new byte[3]);
            out.write(new byte[3]);
            utf(out, "beard");
            out.writeByte(1);
            out.writeByte(9);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(1);    // one item visual
            out.writeByte(8);
            utf(out, "Base.Shirt");
            utf(out, "");
            utf(out, "Shirt");
            out.writeFloat(0.5f);
            for (int i = 0; i < 6; i++) {
                out.writeByte(0);
            }
            utf(out, "");
            out.writeByte(0);
            out.writeByte(1);    // container
            out.writeInt(5);
            utf(out, "inventory");
            out.writeByte(0);
            out.writeShort(0);
            out.writeByte(0);
            out.writeInt(0);
            out.writeByte(1);
            utf(out, "Shirt");
            out.writeShort(0);
            out.writeByte(0);
            out.write(new byte[16]);
            utf(out, "");
            utf(out, "");
            out.write(new byte[2]);
            out.writeByte(0);    // no ragdoll
        });
        if (!squares.containsKey(x * 8 + y)) {
            squares.put(x * 8 + y, new TreeMap<>());
            squares.get(x * 8 + y).put(0, new ArrayList<>());
        }
        return this;
    }

    /** Adds a zombie (class 3), which the decoder does not support. */
    public FakeChunk zombie(int x, int y) {
        return add(x, y, 0, out -> {
            out.writeByte(1);
            out.writeByte(3);
            out.write(new byte[40]);
        });
    }

    /** Sets the world version written in the header. */
    public FakeChunk worldVersion(int version) {
        this.worldVersion = version;
        return this;
    }

    /** Declares vehicles in the chunk (not supported by the decoder). */
    public FakeChunk vehicles(int count) {
        this.vehicles = count;
        return this;
    }

    /** Appends bytes after the content (makes the file inconsistent). */
    public FakeChunk trailingBytes(int count) {
        this.trailingBytes = count;
        return this;
    }

    /** The complete chunk file. */
    public byte[] build() {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);
            out.writeByte(1);       // blending done
            out.writeByte(0x0f);    // blending mask
            out.writeByte(1);       // partial
            out.writeByte(1);       // attachments done
            out.writeByte(0x1f);
            out.writeShort(1);      // one partial attachment
            out.write(new byte[12]);
            out.writeInt(1);        // max level
            out.writeInt(0);        // min level
            out.writeInt(1);        // one floor blood splat
            out.write(new byte[9]);
            for (int index = 0; index < 64; index++) {
                TreeMap<Integer, List<ObjectWriter>> levels = squares.getOrDefault(index, new TreeMap<>());
                long mask = 0;
                for (int level : levels.keySet()) {
                    mask |= 1L << (level + 32);
                }
                out.writeLong(mask);
                for (var entry : levels.entrySet()) {
                    List<ObjectWriter> objects = entry.getValue();
                    out.writeByte(1 | 4);  // erosion data, one category
                    out.write(new byte[3]);
                    out.writeByte(0);      // trees
                    out.writeByte(0);
                    out.writeByte(0);
                    out.writeByte(128);    // explicit stage
                    out.writeByte(2);
                    out.write(new byte[4]);
                    List<ObjectWriter> squareBodies = entry.getKey() == 0 ? bodies.get(index) : null;
                    int flags = squareBodies != null ? 64 : 0;
                    if (!objects.isEmpty()) {
                        flags |= 1 | 8;
                    }
                    out.writeByte(flags);
                    if (!objects.isEmpty()) {
                        out.writeShort(objects.size());
                        for (ObjectWriter w : objects) {
                            out.writeByte(0); // object flags
                            w.write(out);
                        }
                    }
                    if (squareBodies != null) {
                        out.writeByte(1 | 8);
                        out.writeShort(squareBodies.size());
                        for (ObjectWriter w : squareBodies) {
                            w.write(out);
                        }
                        out.write(new byte[12]);
                    }
                    out.writeByte(0);      // visibility
                }
            }
            out.writeByte(1);       // erosion chunk data
            out.write(new byte[17]);
            out.writeShort(1);      // one generator position
            out.write(new byte[9]);
            out.writeShort(vehicles);
            out.write(new byte[vehicles * 3]);
            out.writeInt(0);
            out.writeShort(1);      // one spawned room
            out.writeLong(42);
            out.write(new byte[trailingBytes]);
            out.flush();

            ByteArrayOutputStream file = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(file);
            header.writeByte(0);
            header.writeInt(worldVersion);
            header.writeInt(17 + body.size());
            header.writeLong(0x0123456789L);
            body.writeTo(header);
            header.flush();
            return file.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the base object fields: sprite id, then flags (only extra flags 0 supported here). */
    private static void baseObject(DataOutputStream out, int spriteId, int flags) throws IOException {
        out.writeInt(spriteId);
        out.writeByte(flags);
    }

    private static void utf(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    /** Convenience for tests needing a callback type. */
    public static Consumer<FakeChunk> none() {
        return c -> {
        };
    }
}
