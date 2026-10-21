package zomboidchunkreset.app.map;

import zomboidchunkreset.app.model.SelectionState;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;

/**
 * One click or drag on the map: selects or deselects the rectangle between the press and the
 * release point (a click is a 1 x 1 rectangle), optionally snapped to whole cells.
 * No JavaFX dependency.
 */
public final class SelectionGesture {

    /** What the gesture does to the selection. */
    public enum Kind {
        ADD, REMOVE
    }

    private final Kind kind;
    private final boolean wholeCells;
    private final ChunkPos start;
    private ChunkPos end;

    public SelectionGesture(ChunkPos start, Kind kind, boolean wholeCells) {
        this.start = start;
        this.end = start;
        this.kind = kind;
        this.wholeCells = wholeCells;
    }

    public Kind kind() {
        return kind;
    }

    /** Moves the free corner. */
    public void update(ChunkPos pos) {
        end = pos;
    }

    /** The rectangle the gesture currently covers. */
    public ChunkBounds rectangle() {
        ChunkBounds r = ChunkBounds.of(start, end);
        if (!wholeCells) {
            return r;
        }
        CellPos a = new ChunkPos(r.minX(), r.minY()).cell();
        CellPos b = new ChunkPos(r.maxX(), r.maxY()).cell();
        int side = WorldGeometry.CELL_SIZE_IN_CHUNKS;
        return new ChunkBounds(a.minChunkX(), a.minChunkY(), b.minChunkX() + side - 1, b.minChunkY() + side - 1);
    }

    /** Applies the gesture to the selection (one undo step). */
    public void commit(SelectionState state) {
        ChunkBounds r = rectangle();
        state.edit(s -> {
            if (kind == Kind.ADD) {
                s.addRect(r);
            } else {
                s.removeRect(r);
            }
        });
    }
}
