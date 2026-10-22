package zomboidchunkreset.core.terrain;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import zomboidchunkreset.core.format.ChunkFileHeader;

/**
 * Read-only decoder of the content of a Build 42 chunk file ({@code map/<x>/<y>.bin}), used to draw
 * the map. It walks every square of the chunk and reports, for each square and level, the sprite id
 * of each object on it. Everything else (items, containers, bodies...) is skipped.
 *
 * <p>The layout follows the description of the pzdataspec project (MIT licence, see
 * {@code docs/SAVE_FORMAT.md}) and was checked on real world-version-249 saves: decoding ends exactly
 * at the last byte of every chunk file of the reference saves. Object types that never appeared in
 * those saves are decoded from the description only; characters, animals and vehicles stored in a
 * chunk are not supported and stop the decoding ({@link ChunkContentException}) after the squares
 * already reported.
 *
 * <p>This class never modifies anything; deleting chunks does not depend on it.
 */
public final class ChunkContentReader {

    /** Receives the decoded squares, column by column, levels in increasing order. */
    @FunctionalInterface
    public interface SquareVisitor {
        /**
         * @param localX  0..7, square X inside the chunk
         * @param localY  0..7, square Y inside the chunk
         * @param level   floor level (0 = ground, negative = basement)
         * @param sprites sprite id of each object of the square, in drawing order (bottom first);
         *                only the first {@code count} entries are valid
         */
        void square(int localX, int localY, int level, int[] sprites, int count);
    }

    /** World version of the reference saves. */
    public static final int VERIFIED_WORLD_VERSION = 249;

    /** Squares per chunk side in Build 42. */
    public static final int CHUNK_SIDE = 8;

    private static final int MAX_COUNT = 1_000_000;

    private final ByteBuffer b;
    private final int version;
    private int[] sprites = new int[16];
    private int spriteCount;

    private ChunkContentReader(byte[] data) {
        this.b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        this.version = ChunkFileHeader.parse(b).worldVersion();
    }

    /**
     * Decodes a whole chunk file.
     *
     * @return the world version found in the header
     * @throws ChunkContentException when decoding cannot go on, or when the file does not end exactly
     *                               where the decoding ends; squares reported before remain valid only if
     *                               the world version is {@link #VERIFIED_WORLD_VERSION}
     */
    public static int read(byte[] data, SquareVisitor visitor) throws ChunkContentException {
        if (data.length < ChunkFileHeader.SIZE) {
            throw new ChunkContentException("file shorter than the header");
        }
        ChunkContentReader r = new ChunkContentReader(data);
        try {
            r.readChunk(visitor);
        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            throw new ChunkContentException("truncated or unexpected data", e);
        }
        return r.version;
    }

    // ------------------------------------------------------------------ chunk

    private void readChunk(SquareVisitor visitor) throws ChunkContentException {
        b.position(ChunkFileHeader.SIZE);
        if (b.get(0) != 0) {
            throw new ChunkContentException("debug save layout not supported");
        }
        if (version < 206) {
            throw new ChunkContentException("world version " + version + " not supported");
        }
        // Blending state.
        if (version >= 209) {
            u1();
        }
        if (version >= 210) {
            int mask = u1();
            int partial = u1();
            if (partial != 0 && mask != 0x0f) {
                skip(4);
            }
        }
        // Attachment state.
        if (version >= 214) {
            u1();
            u1();
        }
        if (version >= 221) {
            skip(12L * u2());
        }
        b.getInt(); // max level
        b.getInt(); // min level
        skip(9L * count(b.getInt())); // floor blood splats

        for (int index = 0; index < CHUNK_SIDE * CHUNK_SIDE; index++) {
            long levels = b.getLong();
            for (int bit = 0; bit < 64; bit++) {
                if ((levels & (1L << bit)) == 0) {
                    continue;
                }
                readSquare();
                visitor.square(index / CHUNK_SIDE, index % CHUNK_SIDE, bit - 32, sprites, spriteCount);
            }
        }

        // Erosion state of the chunk.
        if (u1() != 0) {
            skip(17);
        }
        skip(9L * u2()); // generators
        if (u2() != 0) {
            throw new ChunkContentException("vehicles stored in the chunk are not supported");
        }
        b.getInt(); // loot respawn hour
        skip(8L * u2()); // spawned rooms
        if (b.hasRemaining()) {
            throw new ChunkContentException(b.remaining() + " bytes left after the chunk content");
        }
    }

    private void readSquare() throws ChunkContentException {
        erosionSquare();
        int flags = u1();
        int objects = 0;
        if ((flags & 1) != 0) {
            if ((flags & 2) != 0) {
                objects = 2;
            } else if ((flags & 4) != 0) {
                objects = 3;
            } else if ((flags & 8) != 0) {
                objects = u2();
            } else {
                objects = 1;
            }
        }
        spriteCount = 0;
        for (int i = 0; i < objects; i++) {
            u1(); // object flags
            isoObject(true);
        }
        if ((flags & 64) != 0) {
            int extra = u1();
            if ((extra & 1) != 0) {
                int bodies = u2();
                for (int i = 0; i < bodies; i++) {
                    if (isoObject(false) != 11) {
                        throw new ChunkContentException("unexpected object in the body list");
                    }
                }
            }
            if ((extra & 2) != 0) {
                ktable();
            }
            if ((extra & 8) != 0) {
                skip(12);
            }
        }
        u1(); // visibility
    }

    private void erosionSquare() throws ChunkContentException {
        int flags = u1();
        if ((flags & 1) == 0) {
            return;
        }
        skip(3);
        int regions;
        if ((flags & 4) != 0) {
            regions = 1;
        } else if ((flags & 8) != 0) {
            regions = 2;
        } else if ((flags & 16) != 0) {
            regions = 3;
        } else if ((flags & 32) != 0) {
            regions = 4;
        } else if ((flags & 64) != 0) {
            regions = u1();
        } else {
            regions = 0;
        }
        for (int i = 0; i < regions; i++) {
            int region = u1();
            int category = u1();
            u1(); // display season
            int catFlags = u1();
            if ((catFlags & 128) != 0) {
                u1();
            }
            switch (region << 8 | category) {
                case 0x000, 0x001 -> skip(4);      // trees, bushes
                case 0x002 -> skip(3);             // plants
                case 0x003 -> skip(5);             // generic
                case 0x100 -> skip(9);             // street cracks
                case 0x200 -> {                    // wall vines
                    skip(8);
                    if (u1() != 0) {
                        skip(7);
                    }
                }
                case 0x201 -> {                    // wall cracks
                    skip(11);
                    if (u1() != 0) {
                        skip(11);
                    }
                }
                case 0x300 -> skip(1);             // flowerbed
                default -> throw new ChunkContentException("unknown erosion category " + region + "/" + category);
            }
        }
    }

    // ------------------------------------------------------------------ objects

    /**
     * Decodes one serialised object.
     *
     * @param record whether the object's sprite is reported for the current square
     * @return the class id, or -1 for a non-serialised object
     */
    private int isoObject(boolean record) throws ChunkContentException {
        int serialize = u1();
        if (serialize == 0) {
            return -1;
        }
        if (serialize != 1) {
            throw new ChunkContentException("unexpected object header");
        }
        int classId = u1();
        switch (classId) {
            case 6 -> { // world inventory object (no base object)
                skip(20);
                sized();
                skip(8);
                if ((u1() & 2) != 0) {
                    entity();
                }
            }
            case 27 -> { // barricade (no base object)
                u1();
                skip(2L * u1() + 4);
            }
            case 11 -> deadBody();
            case 1, 2, 3, 30, 31, 33, 36 ->
                    throw new ChunkContentException("object class " + classId + " not supported");
            default -> {
                baseObject(record);
                subclass(classId);
            }
        }
        return classId;
    }

    private void baseObject(boolean record) throws ChunkContentException {
        int spriteId = b.getInt();
        if (record) {
            if (spriteCount == sprites.length) {
                sprites = java.util.Arrays.copyOf(sprites, sprites.length * 2);
            }
            sprites[spriteCount++] = spriteId;
        }
        int flags = u1();
        int attached = (flags & 3) == 3 ? 1 : (flags & 1) != 0 ? u1() : 0;
        for (int i = 0; i < attached; i++) {
            b.getInt();
            int spriteFlags = u1();
            if ((spriteFlags & 2) != 0) {
                skip(15);
            }
            if ((spriteFlags & 16) != 0) {
                skip(4);
            }
        }
        if ((flags & 4) != 0) {
            int nameFlags = u1();
            if ((nameFlags & 4) != 0) {
                skip(1);
            } else if ((nameFlags & 8) != 0) {
                string();
            }
            if ((nameFlags & 16) != 0) {
                skip(4);
            } else if ((nameFlags & 32) != 0) {
                string();
            }
        }
        if ((flags & 8) != 0) {
            skip(3);
        }
        if ((flags & 64) != 0) {
            extraData();
        }
    }

    private void extraData() throws ChunkContentException {
        int bits = u2();
        if ((bits & 1) != 0) {
            skip(8L * u1()); // wall blood splats
        }
        if ((bits & 2) != 0) {
            int containers = u1();
            for (int i = 0; i < containers; i++) {
                container();
            }
        }
        if ((bits & 4) != 0) {
            ktable();
        }
        if ((bits & 16) != 0) {
            skip(4);
        }
        if ((bits & 64) != 0) {
            skip(4);
        }
        if ((bits & 128) != 0) {
            skip(4);
        }
        if ((bits & 256) != 0) {
            if ((bits & 512) != 0) {
                string();
            } else {
                skip(4);
            }
        }
        if ((bits & 1024) != 0) {
            skip(4);
        }
        if ((bits & 4096) != 0) {
            entity();
        }
        if ((bits & 8192) != 0) {
            string();
        }
    }

    private void subclass(int classId) throws ChunkContentException {
        switch (classId) {
            case 0, 7, 20, 40 -> {
                // plain object, jukebox, broken glass, butcher hook: nothing more
            }
            case 4 -> { // pushable object
                skip(4);
                if (u1() == 1) {
                    container();
                }
            }
            case 5 -> { // wheelie bin
                if (u1() == 1) {
                    container();
                }
            }
            case 8 -> skip(14);          // curtain
            case 9, 10 -> {              // radio, television
                if (u1() == 1) {
                    deviceData();
                }
            }
            case 12 -> {                 // barbecue
                skip(14);
                for (int i = 0; i < 2; i++) {
                    if (u1() == 1) {
                        skip(4);
                    }
                }
            }
            case 13 -> skip(1);          // clothing dryer
            case 14 -> skip(5);          // clothing washer
            case 15 -> skip(13);         // fireplace
            case 16 -> skip(11);         // stove
            case 17 -> skip(25);         // door
            case 18 -> thumpable();
            case 19 -> trap();
            case 21 -> {                 // car battery charger
                if (u1() == 1) {
                    sized();
                }
                if (u1() == 1) {
                    sized();
                }
                skip(9);
            }
            case 22 -> skip(14);         // generator
            case 23 -> skip(version >= 213 ? 16 : 8); // compost
            case 24 -> mannequin();
            case 26 -> {                 // window
                skip(10);
                for (int i = 0; i < 4; i++) {
                    if (u1() == 1) {
                        skip(4);
                    }
                }
                skip(4);
            }
            case 28 -> skip(2);          // tree
            case 29 -> lightSwitch();
            case 34 -> skip(7);          // combination washer/dryer
            case 35 -> skip(6);          // stacked washer/dryer
            case 37 -> feedingTrough();
            case 38 -> hutch();
            case 39 -> {                 // animal track
                string();
                string();
                skip(8);
                if (u1() == 1) {
                    skip(4);
                }
                skip(9);
            }
            case 41 -> skip(1);          // window frame
            default -> throw new ChunkContentException("object class " + classId + " not supported");
        }
    }

    private void thumpable() throws ChunkContentException {
        long h = b.getLong();
        skipIf(h, 8, 4);
        skipIf(h, 16, 4);
        skipIf(h, 32, 4);
        skipIf(h, 64, 4);
        skipIf(h, 128, 4);
        skipIf(h, 1L << 20, 4);
        if ((h & (1L << 21)) != 0) {
            ktable();
        }
        if ((h & (1L << 22)) != 0) {
            ktable();
        }
        skipIf(h, 1L << 26, 4);
        skipIf(h, 1L << 27, 4);
        skipIf(h, 1L << 28, 4);
        skipIf(h, 1L << 29, 4);
        skipIf(h, 1L << 30, 2);
        skipIf(h, 1L << 31, 4);
        skipIf(h, 1L << 32, 4);
        skipIf(h, 1L << 33, 4);
        skipIf(h, 1L << 37, 4);
        if ((h & (1L << 38)) != 0) {
            string();
        }
        skipIf(h, 1L << 39, 4);
        if ((h & (1L << 42)) != 0) {
            ktable();
        }
    }

    private void skipIf(long header, long bit, int size) {
        if ((header & bit) != 0) {
            skip(size);
        }
    }

    private void trap() {
        skip(24);
        if (version >= 219) {
            skip(8);
        }
        skip(28);
        string();
        string();
        if (u1() == 1) {
            sized();
        }
    }

    private void lightSwitch() {
        u1();
        skip(version >= 206 ? 8 : 4);
        u1();
        if (u1() == 1) {
            u1();
            u1();
            if (u1() == 1) {
                string();
            }
            skip(20);
        }
        skip(12);
    }

    private void feedingTrough() throws ChunkContentException {
        int types = count(b.getInt());
        for (int i = 0; i < types; i++) {
            string();
            skip(4);
        }
        skip(4);
        if (u1() == 1) {
            skip(8);
        }
        u1();
    }

    private void hutch() throws ChunkContentException {
        int linkedX = b.getInt();
        int linkedY = b.getInt();
        b.getInt();
        if (linkedX > 0 && linkedY > 0) {
            return; // secondary square of a hutch
        }
        if (version >= 204) {
            string();
        }
        u1();
        if (version >= 204) {
            u1();
        }
        skip(12);
        if (version < 212) {
            if (u1() != 0) {
                throw new ChunkContentException("animals in a hutch not supported");
            }
        } else {
            // Verified on a real save: this size covers the animal count and the animals.
            skip(count(b.getInt()));
        }
        skip(8);
        int boxes = u1();
        for (int i = 0; i < boxes; i++) {
            int eggs = u1();
            for (int e = 0; e < eggs; e++) {
                sized();
            }
            if (u1() == 1) {
                throw new ChunkContentException("animal in a nest box not supported");
            }
        }
    }

    private void mannequin() throws ChunkContentException {
        skip(5);
        string();
        string();
        humanVisual();
        if (u1() == 1) {
            b.getInt();
            container();
            wornItems();
        }
    }

    private void deadBody() throws ChunkContentException {
        // Moving object part.
        skip(24);
        if (u1() == 1) {
            ktable();
        }
        u1(); // female
        u1(); // was zombie
        if (u1() == 1) { // animal corpse
            string();
            skip(4);
            if (version >= 246) {
                int genes = u1();
                for (int i = 0; i < genes; i++) {
                    b.getInt();
                    string();
                    for (int a = 0; a < 2; a++) {
                        string();
                        skip(9);
                        string();
                    }
                }
                int disorders = u1();
                for (int i = 0; i < disorders; i++) {
                    string();
                }
            }
            string();
            string();
            skip(4);
            string();
            skip(12);
        }
        skip(version >= 199 ? 3 : 2); // network id
        u1();
        b.getInt();
        if (u1() == 1) {
            survivorDescription();
        }
        int visual = u1();
        if (visual == 0) {
            humanVisual();
        } else if (visual == 1) {
            string();
            u1();
        }
        if (u1() == 1) {
            b.getInt();
            container();
            wornItems();
            wornItems();
        }
        skip(4 + 4 + 1 + 1 + 4 + 1);
        if (version >= 222) {
            u1();
        }
        if (version >= 225) {
            string();
            string();
        }
        skip(2);
        if (u1() == 1) {
            skip(44L * count(b.getInt()));
        }
    }

    private void survivorDescription() throws ChunkContentException {
        b.getInt();
        string();
        string();
        string();
        b.getInt();
        string();
        if (b.getInt() == 1) {
            int extras = count(b.getInt());
            for (int i = 0; i < extras; i++) {
                string();
            }
        }
        int boosts = count(b.getInt());
        for (int i = 0; i < boosts; i++) {
            string();
            b.getInt();
        }
        if (version >= 208) {
            string();
            skip(8);
        }
    }

    private void humanVisual() {
        int flags = u1();
        if ((flags & 4) != 0) {
            skip(3);
        }
        if ((flags & 2) != 0) {
            skip(3);
        }
        if ((flags & 8) != 0) {
            skip(3);
        }
        skip(3);
        if ((flags & 64) != 0) {
            string();
        }
        if ((flags & 16) != 0) {
            string();
        }
        if ((flags & 32) != 0) {
            string();
        }
        for (int i = 0; i < 3; i++) {
            skip(u1());
        }
        int items = u1();
        for (int i = 0; i < items; i++) {
            itemVisual();
        }
        string();
        int flags2 = u1();
        if ((flags2 & 4) != 0) {
            skip(3);
        }
        if ((flags2 & 2) != 0) {
            skip(3);
        }
    }

    private void itemVisual() {
        int flags = u1();
        string();
        string();
        string();
        if ((flags & 1) != 0) {
            skip(3);
        }
        if ((flags & 2) != 0) {
            skip(1);
        }
        if ((flags & 4) != 0) {
            skip(1);
        }
        if ((flags & 8) != 0) {
            skip(4);
        }
        if ((flags & 16) != 0) {
            string();
        }
        for (int i = 0; i < 6; i++) {
            skip(u1());
        }
    }

    private void wornItems() {
        int n = u1();
        for (int i = 0; i < n; i++) {
            string();
            skip(2);
        }
    }

    private void deviceData() throws ChunkContentException {
        string();
        skip(48);
        if (u1() == 1) {
            b.getInt();
            int presets = count(b.getInt());
            for (int i = 0; i < presets; i++) {
                string();
                skip(4);
            }
        }
        skip(3);
        if (u1() == 1) {
            string();
        }
        u1();
    }

    // ------------------------------------------------------------------ shared structures

    private void container() throws ChunkContentException {
        string();
        u1();
        int groups = u2();
        for (int i = 0; i < groups; i++) {
            int identical = b.getInt();
            sized();
            if (identical > 1) {
                skip(4L * count(identical - 1));
            }
        }
        u1();
        b.getInt();
    }

    /** An item saved with its size in front. */
    private void sized() {
        int size = b.getInt();
        if (size < 0) {
            throw new IndexOutOfBoundsException("negative size");
        }
        skip(size);
    }

    private void entity() {
        int components = u1();
        for (int i = 0; i < components; i++) {
            int size = b.getInt();
            if (size < 2) {
                throw new IndexOutOfBoundsException("bad component size");
            }
            skip(size);
        }
    }

    private void ktable() throws ChunkContentException {
        int entries = count(b.getInt());
        for (int i = 0; i < entries; i++) {
            kobject();
            kobject();
        }
    }

    private void kobject() throws ChunkContentException {
        switch (u1()) {
            case 0 -> string();
            case 1 -> skip(8);
            case 2 -> ktable();
            case 3 -> skip(1);
            default -> throw new ChunkContentException("unknown table value type");
        }
    }

    private void string() {
        skip(u2());
    }

    private int u1() {
        return b.get() & 0xFF;
    }

    private int u2() {
        return b.getShort() & 0xFFFF;
    }

    private void skip(long n) {
        if (n < 0 || n > b.remaining()) {
            throw new IndexOutOfBoundsException("skip past the end");
        }
        b.position(b.position() + (int) n);
    }

    private static int count(int n) throws ChunkContentException {
        if (n < 0 || n > MAX_COUNT) {
            throw new ChunkContentException("implausible count " + n);
        }
        return n;
    }
}
