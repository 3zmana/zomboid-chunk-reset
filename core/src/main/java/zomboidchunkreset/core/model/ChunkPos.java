package zomboidchunkreset.core.model;

import java.util.Comparator;

/**
 * Position of a chunk, in chunk coordinates.
 *
 * @param x chunk X (equals {@code floor(squareX / 8)})
 * @param y chunk Y (equals {@code floor(squareY / 8)})
 */
public record ChunkPos(int x, int y) implements Comparable<ChunkPos> {

    private static final Comparator<ChunkPos> ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y);

    /** Returns the chunk containing the given square. */
    public static ChunkPos ofSquare(int squareX, int squareY) {
        return new ChunkPos(WorldGeometry.squareToChunk(squareX), WorldGeometry.squareToChunk(squareY));
    }

    /** Returns the chunk containing the given (possibly fractional) square position. */
    public static ChunkPos ofSquare(double squareX, double squareY) {
        return new ChunkPos(WorldGeometry.squareToChunk(squareX), WorldGeometry.squareToChunk(squareY));
    }

    /** Rebuilds a position from {@link #toKey()}. */
    public static ChunkPos fromKey(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }

    /** Packs this position into a single long, convenient for compact sets and maps. */
    public long toKey() {
        return ((long) x << 32) | (y & 0xFFFF_FFFFL);
    }

    /** The cell this chunk belongs to. */
    public CellPos cell() {
        return new CellPos(WorldGeometry.chunkToCell(x), WorldGeometry.chunkToCell(y));
    }

    /** In-game X of the first (west-most) square of this chunk. */
    public int minSquareX() {
        return x * WorldGeometry.CHUNK_SIZE_IN_SQUARES;
    }

    /** In-game Y of the first (north-most) square of this chunk. */
    public int minSquareY() {
        return y * WorldGeometry.CHUNK_SIZE_IN_SQUARES;
    }

    /** In-game X of the last square of this chunk (inclusive). */
    public int maxSquareX() {
        return minSquareX() + WorldGeometry.CHUNK_SIZE_IN_SQUARES - 1;
    }

    /** In-game Y of the last square of this chunk (inclusive). */
    public int maxSquareY() {
        return minSquareY() + WorldGeometry.CHUNK_SIZE_IN_SQUARES - 1;
    }

    @Override
    public int compareTo(ChunkPos other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return "chunk(" + x + ", " + y + ")";
    }
}
