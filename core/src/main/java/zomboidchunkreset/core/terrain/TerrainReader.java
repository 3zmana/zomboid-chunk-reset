package zomboidchunkreset.core.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Turns a chunk file into its {@link ChunkTerrain} (top view), using a {@link TileTable}.
 *
 * <p>Rules for each square, seen from above:
 * <ol>
 *   <li>the highest upper level (1 and above) holding a roof tile gives {@link TerrainCategory#ROOF};
 *       one holding a floor or ceiling tile gives {@link TerrainCategory#ROOF_FLAT};</li>
 *   <li>otherwise, on the ground level: a tree gives {@link TerrainCategory#TREE}, then a wall or
 *       fence {@link TerrainCategory#WALL};</li>
 *   <li>otherwise the lowest object (the floor) gives the ground type; natural ground with tall
 *       grass, bushes or plants on it gives {@link TerrainCategory#TALL_GRASS}.</li>
 * </ol>
 * Basements are ignored.
 */
public final class TerrainReader {

    private static final int SQUARES = ChunkTerrain.SIDE * ChunkTerrain.SIDE;

    private final TileTable table;

    public TerrainReader(TileTable table) {
        this.table = table;
    }

    /** Reads a chunk file; empty when nothing reliable could be decoded. */
    public Optional<ChunkTerrain> read(Path chunkFile) throws IOException {
        return read(Files.readAllBytes(chunkFile));
    }

    /**
     * Decodes chunk file content; empty when nothing reliable could be decoded. A partly decoded chunk
     * is kept only for the verified world version, where the squares before the failure are known to
     * be right.
     */
    public Optional<ChunkTerrain> read(byte[] data) {
        byte[] ground = new byte[SQUARES];
        byte[] roof = new byte[SQUARES];
        int[] roofLevel = new int[SQUARES];
        Arrays.fill(roofLevel, Integer.MIN_VALUE);
        boolean[] seen = new boolean[SQUARES];
        boolean complete;
        try {
            ChunkContentReader.read(data, (x, y, level, sprites, count) -> {
                int i = x * ChunkTerrain.SIDE + y;
                seen[i] = true;
                if (level == 0) {
                    ground[i] = (byte) ground(sprites, count).ordinal();
                } else if (level > 0 && level >= roofLevel[i]) {
                    TerrainCategory r = roof(sprites, count);
                    if (r != null) {
                        roof[i] = (byte) r.ordinal();
                        roofLevel[i] = level;
                    }
                }
            });
            complete = true;
        } catch (ChunkContentException e) {
            complete = false;
            if (data.length < 5 || headerVersion(data) != ChunkContentReader.VERIFIED_WORLD_VERSION) {
                return Optional.empty();
            }
        }
        byte[] result = new byte[SQUARES];
        boolean any = false;
        for (int i = 0; i < SQUARES; i++) {
            if (roofLevel[i] != Integer.MIN_VALUE) {
                result[i] = roof[i];
            } else {
                result[i] = ground[i];
            }
            any |= seen[i];
        }
        if (!any) {
            return Optional.empty();
        }
        if (!complete) {
            fillMissing(result, seen);
        }
        return Optional.of(new ChunkTerrain(result, complete));
    }

    private static int headerVersion(byte[] data) {
        return ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
    }

    /** Squares that were not reached take the most common category of the others. */
    private static void fillMissing(byte[] result, boolean[] seen) {
        int[] counts = new int[TerrainCategory.values().length];
        for (int i = 0; i < result.length; i++) {
            if (seen[i] && result[i] != TerrainCategory.NONE.ordinal()) {
                counts[result[i]]++;
            }
        }
        int best = TerrainCategory.NONE.ordinal();
        for (int c = 0; c < counts.length; c++) {
            if (counts[c] > counts[best]) {
                best = c;
            }
        }
        for (int i = 0; i < result.length; i++) {
            if (!seen[i]) {
                result[i] = (byte) best;
            }
        }
    }

    /** Category of the ground level of a square. */
    TerrainCategory ground(int[] sprites, int count) {
        if (count == 0) {
            return TerrainCategory.NONE;
        }
        boolean tree = false;
        boolean wall = false;
        boolean vegetation = false;
        for (int k = 0; k < count; k++) {
            switch (table.classOf(sprites[k])) {
                case TREE -> tree = true;
                case WALL -> wall = true;
                case VEGETATION -> vegetation = true;
                default -> {
                    // not relevant here
                }
            }
        }
        if (tree) {
            return TerrainCategory.TREE;
        }
        if (wall) {
            return TerrainCategory.WALL;
        }
        TileClass floor = table.classOf(sprites[0]);
        if (vegetation && floor.isNaturalGround()) {
            return TerrainCategory.TALL_GRASS;
        }
        return switch (floor) {
            case WATER -> TerrainCategory.WATER;
            case ROAD -> TerrainCategory.ROAD;
            case PAVEMENT -> TerrainCategory.PAVEMENT;
            case SAND -> TerrainCategory.SAND;
            case DIRT -> TerrainCategory.DIRT;
            case GRASS_LIGHT -> TerrainCategory.GRASS_LIGHT;
            case GRASS_MEDIUM -> TerrainCategory.GRASS_MEDIUM;
            case GRASS_DARK -> TerrainCategory.GRASS_DARK;
            case FLOOR_INTERIOR -> TerrainCategory.INTERIOR;
            case VEGETATION -> TerrainCategory.TALL_GRASS;
            default -> TerrainCategory.NEUTRAL;
        };
    }

    /** Roof category of an upper level of a square, or {@code null} if it covers nothing. */
    TerrainCategory roof(int[] sprites, int count) {
        TerrainCategory result = null;
        for (int k = 0; k < count; k++) {
            TileClass c = table.classOf(sprites[k]);
            if (c == TileClass.ROOF) {
                return TerrainCategory.ROOF;
            }
            if (c == TileClass.CEILING || c.isFloor()) {
                result = TerrainCategory.ROOF_FLAT;
            }
        }
        return result;
    }
}
