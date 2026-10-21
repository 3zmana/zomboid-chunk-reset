package zomboidchunkreset.core.db;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * A vehicle row of {@code vehicles.db} (the binary {@code data} column is not read).
 *
 * @param id          primary key
 * @param storedChunk chunk stored in the {@code wx}/{@code wy} columns
 * @param x           in-game X (square coordinate)
 * @param y           in-game Y (square coordinate)
 */
public record VehicleRecord(long id, ChunkPos storedChunk, double x, double y) {

    /** The chunk computed from the vehicle position; equal to {@link #storedChunk()} in valid saves. */
    public ChunkPos chunkFromPosition() {
        return ChunkPos.ofSquare(x, y);
    }
}
