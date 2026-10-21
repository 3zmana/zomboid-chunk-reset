package zomboidchunkreset.app.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;

/**
 * What the map displays, derived once from a {@link SaveIndex}: a dense grid of chunk "levels"
 * (0 = no chunk file, 1..{@link #LEVELS} = file size bucket) over a cell-aligned region, plus
 * player markers. No JavaFX dependency.
 */
public final class MapModel {

    /** Number of file-size shades. */
    public static final int LEVELS = 8;

    /** A player position, in fractional chunk coordinates. */
    public record PlayerMarker(String name, double chunkX, double chunkY) {
    }

    private final ChunkBounds region;
    private final ChunkBounds dataBounds;
    private final byte[] levels;
    private final List<PlayerMarker> players;

    private MapModel(ChunkBounds region, ChunkBounds dataBounds, byte[] levels, List<PlayerMarker> players) {
        this.region = region;
        this.dataBounds = dataBounds;
        this.levels = levels;
        this.players = List.copyOf(players);
    }

    /** Builds the model of a scanned save. */
    public static MapModel of(SaveIndex index) {
        List<PlayerMarker> players = new ArrayList<>();
        for (PlayerRecord p : index.players()) {
            players.add(new PlayerMarker(p.displayName(), p.x() / WorldGeometry.CHUNK_SIZE_IN_SQUARES,
                    p.y() / WorldGeometry.CHUNK_SIZE_IN_SQUARES));
        }
        Optional<ChunkBounds> bounds = index.bounds();
        if (bounds.isEmpty()) {
            return new MapModel(null, null, new byte[0], players);
        }
        ChunkBounds data = bounds.get();
        // Whole cells, plus one cell of margin around the data.
        int side = WorldGeometry.CELL_SIZE_IN_CHUNKS;
        CellPos min = new ChunkPos(data.minX(), data.minY()).cell();
        CellPos max = new ChunkPos(data.maxX(), data.maxY()).cell();
        ChunkBounds region = new ChunkBounds((min.x() - 1) * side, (min.y() - 1) * side,
                (max.x() + 2) * side - 1, (max.y() + 2) * side - 1);

        long minSize = Long.MAX_VALUE;
        long maxSize = 0;
        for (ChunkEntry e : index.chunks().values()) {
            minSize = Math.min(minSize, Math.max(1, e.fileSize()));
            maxSize = Math.max(maxSize, e.fileSize());
        }
        double logMin = Math.log(minSize);
        double logRange = Math.max(1e-9, Math.log(Math.max(maxSize, minSize + 1)) - logMin);
        byte[] levels = new byte[region.width() * region.height()];
        for (ChunkEntry e : index.chunks().values()) {
            double t = (Math.log(Math.max(1, e.fileSize())) - logMin) / logRange;
            int level = 1 + (int) Math.min(LEVELS - 1, Math.max(0, Math.floor(t * LEVELS)));
            levels[offset(region, e.pos().x(), e.pos().y())] = (byte) level;
        }
        return new MapModel(region, data, levels, players);
    }

    private static int offset(ChunkBounds region, int x, int y) {
        return (y - region.minY()) * region.width() + (x - region.minX());
    }

    /** Whether the save has no chunk at all. */
    public boolean isEmpty() {
        return region == null;
    }

    /** The displayed region (cell-aligned, with a margin), empty for a save without chunks. */
    public Optional<ChunkBounds> region() {
        return Optional.ofNullable(region);
    }

    /** Bounds of the saved chunks, empty for a save without chunks. */
    public Optional<ChunkBounds> dataBounds() {
        return Optional.ofNullable(dataBounds);
    }

    /** Whether the position is inside the displayed region. */
    public boolean inRegion(int x, int y) {
        return region != null && x >= region.minX() && x <= region.maxX() && y >= region.minY() && y <= region.maxY();
    }

    /** 0 if there is no chunk file here, otherwise its size shade (1..{@link #LEVELS}). */
    public int level(int x, int y) {
        return inRegion(x, y) ? levels[offset(region, x, y)] : 0;
    }

    /** Whether a chunk file exists here. */
    public boolean hasChunk(int x, int y) {
        return level(x, y) != 0;
    }

    /** Player markers. */
    public List<PlayerMarker> players() {
        return players;
    }
}
