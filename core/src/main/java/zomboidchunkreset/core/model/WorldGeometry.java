package zomboidchunkreset.core.model;

/**
 * Size constants of the Build 42 world grid, as verified on real save files
 * (see docs/SAVE_FORMAT.md).
 *
 * <ul>
 *   <li>A <b>square</b> is one in-game tile; in-game coordinates (X, Y) are square coordinates.</li>
 *   <li>A <b>chunk</b> is 8 x 8 squares. Each saved chunk is one file {@code map/<chunkX>/<chunkY>.bin}.</li>
 *   <li>A <b>cell</b> is 32 x 32 chunks (256 x 256 squares). Several per-cell files use cell coordinates.</li>
 * </ul>
 */
public final class WorldGeometry {

    /** Number of squares along one side of a chunk. */
    public static final int CHUNK_SIZE_IN_SQUARES = 8;

    /** Number of chunks along one side of a cell. */
    public static final int CELL_SIZE_IN_CHUNKS = 32;

    /** Number of squares along one side of a cell. */
    public static final int CELL_SIZE_IN_SQUARES = CHUNK_SIZE_IN_SQUARES * CELL_SIZE_IN_CHUNKS;

    private WorldGeometry() {
    }

    /** Converts an integer square coordinate to the chunk coordinate that contains it. */
    public static int squareToChunk(int square) {
        return Math.floorDiv(square, CHUNK_SIZE_IN_SQUARES);
    }

    /**
     * Converts a (possibly fractional) square coordinate, as stored for players and vehicles,
     * to the chunk coordinate that contains it.
     */
    public static int squareToChunk(double square) {
        return (int) Math.floor(square / CHUNK_SIZE_IN_SQUARES);
    }

    /** Converts a chunk coordinate to the cell coordinate that contains it. */
    public static int chunkToCell(int chunk) {
        return Math.floorDiv(chunk, CELL_SIZE_IN_CHUNKS);
    }

    /** Converts an integer square coordinate to the cell coordinate that contains it. */
    public static int squareToCell(int square) {
        return Math.floorDiv(square, CELL_SIZE_IN_SQUARES);
    }
}
