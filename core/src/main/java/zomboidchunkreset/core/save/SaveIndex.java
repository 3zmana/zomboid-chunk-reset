package zomboidchunkreset.core.save;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;

/**
 * Everything a scan found in a save folder. Immutable snapshot: it does not follow later changes
 * on disk.
 */
public final class SaveIndex {

    private final Path root;
    private final SaveKind kind;
    private final List<String> kindSignals;
    private final Map<ChunkPos, ChunkEntry> chunks;
    private final ChunkBounds bounds;
    private final Set<Integer> chunkWorldVersions;
    private final Map<CellFileType, Map<CellPos, Path>> cellFiles;
    private final RegionHeader regionHeader;
    private final Map<ChunkPos, Path> isoRegionFiles;
    private final List<PlayerRecord> players;
    private final List<VehicleRecord> vehicles;
    private final List<String> globalFilesWithPositions;
    private final List<ScanIssue> issues;

    SaveIndex(Path root, SaveKind kind, List<String> kindSignals, Map<ChunkPos, ChunkEntry> chunks,
              ChunkBounds bounds, Set<Integer> chunkWorldVersions, Map<CellFileType, Map<CellPos, Path>> cellFiles,
              RegionHeader regionHeader, Map<ChunkPos, Path> isoRegionFiles, List<PlayerRecord> players,
              List<VehicleRecord> vehicles, List<String> globalFilesWithPositions, List<ScanIssue> issues) {
        this.root = root;
        this.kind = kind;
        this.kindSignals = List.copyOf(kindSignals);
        this.chunks = Collections.unmodifiableMap(chunks);
        this.bounds = bounds;
        this.chunkWorldVersions = Set.copyOf(chunkWorldVersions);
        EnumMap<CellFileType, Map<CellPos, Path>> copy = new EnumMap<>(CellFileType.class);
        for (CellFileType type : CellFileType.values()) {
            copy.put(type, Collections.unmodifiableMap(cellFiles.getOrDefault(type, Map.of())));
        }
        this.cellFiles = Collections.unmodifiableMap(copy);
        this.regionHeader = regionHeader;
        this.isoRegionFiles = Collections.unmodifiableMap(isoRegionFiles);
        this.players = List.copyOf(players);
        this.vehicles = List.copyOf(vehicles);
        this.globalFilesWithPositions = List.copyOf(globalFilesWithPositions);
        this.issues = List.copyOf(issues);
    }

    /** The save folder. */
    public Path root() {
        return root;
    }

    /** Detected kind of save. */
    public SaveKind kind() {
        return kind;
    }

    /** The files or rows that led to {@link #kind()}, for display. */
    public List<String> kindSignals() {
        return kindSignals;
    }

    /** All saved chunks, by position. */
    public Map<ChunkPos, ChunkEntry> chunks() {
        return chunks;
    }

    /** Whether a chunk file exists for this position. */
    public boolean hasChunk(ChunkPos pos) {
        return chunks.containsKey(pos);
    }

    /** Bounds of all saved chunks, empty if the save has none. */
    public Optional<ChunkBounds> bounds() {
        return Optional.ofNullable(bounds);
    }

    /** World versions read from chunk headers (empty if headers were not read). */
    public Set<Integer> chunkWorldVersions() {
        return chunkWorldVersions;
    }

    /** Total size of all chunk files, in bytes. */
    public long totalChunkBytes() {
        long total = 0;
        for (ChunkEntry e : chunks.values()) {
            total += e.fileSize();
        }
        return total;
    }

    /** Number of saved chunks in each cell, sorted by cell. */
    public Map<CellPos, Integer> chunkCountByCell() {
        Map<CellPos, Integer> counts = new TreeMap<>();
        for (ChunkPos pos : chunks.keySet()) {
            counts.merge(pos.cell(), 1, Integer::sum);
        }
        return counts;
    }

    /** Per-cell files of the given type, by cell. */
    public Map<CellPos, Path> cellFiles(CellFileType type) {
        return cellFiles.get(type);
    }

    /** Parsed {@code RegionHeader.bin}, empty if missing or unreadable. */
    public Optional<RegionHeader> regionHeader() {
        return Optional.ofNullable(regionHeader);
    }

    /** {@code isoregiondata/datachunk_X_Y.bin} files, by chunk. */
    public Map<ChunkPos, Path> isoRegionFiles() {
        return isoRegionFiles;
    }

    /** Players read from {@code players.db}. */
    public List<PlayerRecord> players() {
        return players;
    }

    /** Vehicles read from {@code vehicles.db}. */
    public List<VehicleRecord> vehicles() {
        return vehicles;
    }

    /**
     * Global files present in this save that are known to contain world positions
     * (see {@link SaveScanner#GLOBAL_FILES_WITH_POSITIONS}). They are not modified by this program.
     */
    public List<String> globalFilesWithPositions() {
        return globalFilesWithPositions;
    }

    /** Problems found during the scan. */
    public List<ScanIssue> issues() {
        return issues;
    }
}
