package zomboidchunkreset.core.reset;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;

/**
 * What a reset would do, computed by {@link ResetPlanner} without touching the disk. Shown to the
 * user as a preview, then carried out (or simulated) by {@link ResetExecutor}.
 */
public final class ResetPlan {

    private final SaveIndex index;
    private final ResetMode mode;
    private final ResetOptions options;
    private final List<ChunkEntry> chunksToDelete;
    private final Set<CellPos> cellsToReset;
    private final Map<CellFileType, Map<CellPos, Path>> cellFilesToDelete;
    private final Map<ChunkPos, Path> isoFilesToDelete;
    private final RegionHeader newRegionHeader;
    private final List<VehicleRecord> vehiclesToDelete;
    private final List<PlayerRecord> playersInResetArea;
    private final List<PlanWarning> warnings;

    ResetPlan(SaveIndex index, ResetMode mode, ResetOptions options, List<ChunkEntry> chunksToDelete,
              Set<CellPos> cellsToReset, Map<CellFileType, Map<CellPos, Path>> cellFilesToDelete,
              Map<ChunkPos, Path> isoFilesToDelete, RegionHeader newRegionHeader,
              List<VehicleRecord> vehiclesToDelete, List<PlayerRecord> playersInResetArea,
              List<PlanWarning> warnings) {
        this.index = index;
        this.mode = mode;
        this.options = options;
        this.chunksToDelete = List.copyOf(chunksToDelete);
        this.cellsToReset = Collections.unmodifiableSet(cellsToReset);
        EnumMap<CellFileType, Map<CellPos, Path>> copy = new EnumMap<>(CellFileType.class);
        for (CellFileType type : CellFileType.values()) {
            copy.put(type, Collections.unmodifiableMap(cellFilesToDelete.getOrDefault(type, Map.of())));
        }
        this.cellFilesToDelete = Collections.unmodifiableMap(copy);
        this.isoFilesToDelete = Collections.unmodifiableMap(isoFilesToDelete);
        this.newRegionHeader = newRegionHeader;
        this.vehiclesToDelete = List.copyOf(vehiclesToDelete);
        this.playersInResetArea = List.copyOf(playersInResetArea);
        this.warnings = List.copyOf(warnings);
    }

    /** The scan this plan was computed from. */
    public SaveIndex index() {
        return index;
    }

    public ResetMode mode() {
        return mode;
    }

    public ResetOptions options() {
        return options;
    }

    /** Chunk files to delete, sorted by position. */
    public List<ChunkEntry> chunksToDelete() {
        return chunksToDelete;
    }

    /** Total size of the chunk files to delete, in bytes. */
    public long chunkBytesToDelete() {
        long total = 0;
        for (ChunkEntry e : chunksToDelete) {
            total += e.fileSize();
        }
        return total;
    }

    /**
     * Cells that will have no chunk file left and whose per-cell files are deleted (according to
     * the options), sorted.
     */
    public Set<CellPos> cellsToReset() {
        return cellsToReset;
    }

    /** Per-cell files to delete, by type and cell. */
    public Map<CellPos, Path> cellFilesToDelete(CellFileType type) {
        return cellFilesToDelete.get(type);
    }

    /** Total number of per-cell files to delete. */
    public int cellFileCount() {
        int n = 0;
        for (Map<CellPos, Path> files : cellFilesToDelete.values()) {
            n += files.size();
        }
        return n;
    }

    /** {@code datachunk} files to delete, by chunk. */
    public Map<ChunkPos, Path> isoFilesToDelete() {
        return isoFilesToDelete;
    }

    /** The current {@code RegionHeader.bin}, if the save has a readable one. */
    public Optional<RegionHeader> originalRegionHeader() {
        return index.regionHeader();
    }

    /** The {@code RegionHeader.bin} to write, or empty if it does not change. */
    public Optional<RegionHeader> newRegionHeader() {
        return Optional.ofNullable(newRegionHeader);
    }

    /** Rows of {@code vehicles.db} to delete. */
    public List<VehicleRecord> vehiclesToDelete() {
        return vehiclesToDelete;
    }

    /** Players standing in chunks that will be reset ({@code players.db} is never modified). */
    public List<PlayerRecord> playersInResetArea() {
        return playersInResetArea;
    }

    /** Warnings to show before running. */
    public List<PlanWarning> warnings() {
        return warnings;
    }

    /** Whether the plan contains a warning of this type. */
    public boolean hasWarning(PlanWarning.Type type) {
        return warnings.stream().anyMatch(w -> w.type() == type);
    }

    /** Number of files the plan deletes (chunk, per-cell and iso-region files). */
    public int filesToDelete() {
        return chunksToDelete.size() + cellFileCount() + isoFilesToDelete.size();
    }

    /** Whether running this plan would change nothing. */
    public boolean isEmpty() {
        return filesToDelete() == 0 && newRegionHeader == null && vehiclesToDelete.isEmpty();
    }
}
