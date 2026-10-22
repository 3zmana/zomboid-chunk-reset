package zomboidchunkreset.app.map;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.terrain.ChunkTerrain;

/**
 * Top views of the chunks of the displayed region, filled in the background while the map is shown.
 * Thread-safe: written by loader threads, read by the renderer. No JavaFX dependency.
 */
public final class TerrainLayer {

    private final ChunkBounds region;
    private final AtomicReferenceArray<ChunkTerrain> chunks;
    private final AtomicInteger loaded = new AtomicInteger();

    /** An empty layer covering {@code region} (or nothing, if {@code null}). */
    public TerrainLayer(ChunkBounds region) {
        this.region = region;
        this.chunks = new AtomicReferenceArray<>(region == null ? 0 : region.width() * region.height());
    }

    /** Stores the top view of a chunk (ignored outside the region or when {@code null}). */
    public void put(int x, int y, ChunkTerrain terrain) {
        if (terrain != null && contains(x, y) && chunks.getAndSet(offset(x, y), terrain) == null) {
            loaded.incrementAndGet();
        }
    }

    /** Top view of a chunk, or {@code null} if not (yet) available. */
    public ChunkTerrain get(int x, int y) {
        return contains(x, y) ? chunks.get(offset(x, y)) : null;
    }

    /** Number of chunks with a top view. */
    public int loadedCount() {
        return loaded.get();
    }

    private boolean contains(int x, int y) {
        return region != null && x >= region.minX() && x <= region.maxX() && y >= region.minY() && y <= region.maxY();
    }

    private int offset(int x, int y) {
        return (y - region.minY()) * region.width() + (x - region.minX());
    }
}
