package zomboidchunkreset.core.model;

/**
 * Inclusive rectangle of chunk coordinates.
 */
public record ChunkBounds(int minX, int minY, int maxX, int maxY) {

    public ChunkBounds {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Empty bounds: " + minX + "," + minY + " .. " + maxX + "," + maxY);
        }
    }

    /** Smallest bounds containing both corners, whatever their order. */
    public static ChunkBounds of(ChunkPos a, ChunkPos b) {
        return new ChunkBounds(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()), Math.max(a.y(), b.y()));
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public boolean contains(ChunkPos chunk) {
        return chunk.x() >= minX && chunk.x() <= maxX && chunk.y() >= minY && chunk.y() <= maxY;
    }

    /** Returns these bounds grown to also contain the given chunk. */
    public ChunkBounds including(ChunkPos chunk) {
        return new ChunkBounds(Math.min(minX, chunk.x()), Math.min(minY, chunk.y()),
                Math.max(maxX, chunk.x()), Math.max(maxY, chunk.y()));
    }
}
