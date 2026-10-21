package zomboidchunkreset.core.model;

import java.util.Comparator;

/**
 * Position of a cell (32 x 32 chunks, 256 x 256 squares), in cell coordinates.
 * Used by the per-cell files: {@code zpop_X_Y.bin}, {@code apop_X_Y.bin},
 * {@code chunkdata_X_Y.bin} and {@code metagrid/metacell_X_Y.bin}.
 */
public record CellPos(int x, int y) implements Comparable<CellPos> {

    private static final Comparator<CellPos> ORDER =
            Comparator.comparingInt(CellPos::x).thenComparingInt(CellPos::y);

    /** Returns the cell containing the given square. */
    public static CellPos ofSquare(int squareX, int squareY) {
        return new CellPos(WorldGeometry.squareToCell(squareX), WorldGeometry.squareToCell(squareY));
    }

    /** X of the first chunk of this cell. */
    public int minChunkX() {
        return x * WorldGeometry.CELL_SIZE_IN_CHUNKS;
    }

    /** Y of the first chunk of this cell. */
    public int minChunkY() {
        return y * WorldGeometry.CELL_SIZE_IN_CHUNKS;
    }

    /** Whether the given chunk lies inside this cell. */
    public boolean contains(ChunkPos chunk) {
        return chunk.cell().equals(this);
    }

    @Override
    public int compareTo(CellPos other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return "cell(" + x + ", " + y + ")";
    }
}
