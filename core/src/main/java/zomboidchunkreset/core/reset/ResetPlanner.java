package zomboidchunkreset.core.reset;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.db.VehiclesDatabase;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.ScanIssue;
import zomboidchunkreset.core.selection.ChunkSelection;

/**
 * Computes a {@link ResetPlan}. Pure computation on a {@link SaveIndex}: nothing is read from or
 * written to disk.
 *
 * <h2>Rules (see docs/DELETION_RULES.md)</h2>
 * <ul>
 *   <li>The <i>reset area</i> is the selection ({@link ResetMode#DELETE_SELECTED}) or everything
 *       outside it ({@link ResetMode#KEEP_SELECTED}). Chunks are always reset whole.</li>
 *   <li>Every chunk file in the reset area is deleted.</li>
 *   <li>A cell is <i>fully reset</i> when no chunk file of it remains afterwards, and either at least
 *       one of its chunk files is deleted or the cell lies entirely in the reset area. Only then are
 *       its per-cell files deleted: {@code chunkdata} and {@code metacell} always, {@code zpop} and
 *       {@code apop} if enabled. A cell that keeps even one chunk keeps all its per-cell files.</li>
 *   <li>Iso regions (if enabled): {@code datachunk} files in the reset area are deleted and
 *       {@code RegionHeader.bin} is rewritten without them. Skipped if the header is unreadable.</li>
 *   <li>Vehicles (if enabled): rows whose stored chunk is in the reset area are deleted.</li>
 *   <li>{@code players.db} and every global file are never modified; warnings are produced.</li>
 * </ul>
 */
public final class ResetPlanner {

    private static final int CHUNKS_PER_CELL =
            WorldGeometry.CELL_SIZE_IN_CHUNKS * WorldGeometry.CELL_SIZE_IN_CHUNKS;

    private ResetPlanner() {
    }

    /** Computes what resetting with the given selection, mode and options would do. */
    public static ResetPlan plan(SaveIndex index, ChunkSelection selection, ResetMode mode, ResetOptions options) {
        ChunkSelection sel = selection.copy();
        Predicate<ChunkPos> inResetArea = mode == ResetMode.DELETE_SELECTED ? sel::contains : p -> !sel.contains(p);
        List<PlanWarning> warnings = new ArrayList<>();

        // Chunk files
        List<ChunkEntry> chunksToDelete = new ArrayList<>();
        Map<CellPos, Integer> deletedPerCell = new HashMap<>();
        Map<CellPos, Integer> remainingPerCell = new HashMap<>();
        for (ChunkEntry e : index.chunks().values()) {
            if (inResetArea.test(e.pos())) {
                chunksToDelete.add(e);
                deletedPerCell.merge(e.pos().cell(), 1, Integer::sum);
            } else {
                remainingPerCell.merge(e.pos().cell(), 1, Integer::sum);
            }
        }
        chunksToDelete.sort(Comparator.comparing(ChunkEntry::pos));

        // Fully reset cells
        Map<CellPos, Integer> selectedPerCell = sel.countByCell();
        Set<CellPos> candidates = new HashSet<>(deletedPerCell.keySet());
        for (CellFileType type : CellFileType.values()) {
            candidates.addAll(index.cellFiles(type).keySet());
        }
        Set<CellPos> cellsToReset = new TreeSet<>();
        for (CellPos cell : candidates) {
            if (remainingPerCell.getOrDefault(cell, 0) > 0) {
                continue;
            }
            int selected = selectedPerCell.getOrDefault(cell, 0);
            boolean entirelyInResetArea = mode == ResetMode.DELETE_SELECTED
                    ? selected == CHUNKS_PER_CELL
                    : selected == 0;
            if (deletedPerCell.getOrDefault(cell, 0) > 0 || entirelyInResetArea) {
                cellsToReset.add(cell);
            }
        }
        Map<CellFileType, Map<CellPos, Path>> cellFiles = new EnumMap<>(CellFileType.class);
        for (CellFileType type : CellFileType.values()) {
            boolean enabled = switch (type) {
                case ZOMBIE_POPULATION -> options.zombiePopulation();
                case ANIMAL_POPULATION -> options.animalPopulation();
                case CHUNK_DATA, META_CELL -> true;
            };
            Map<CellPos, Path> files = new TreeMap<>();
            if (enabled) {
                for (Map.Entry<CellPos, Path> e : index.cellFiles(type).entrySet()) {
                    if (cellsToReset.contains(e.getKey())) {
                        files.put(e.getKey(), e.getValue());
                    }
                }
            }
            cellFiles.put(type, files);
        }

        // Iso regions
        Map<ChunkPos, Path> isoToDelete = new TreeMap<>();
        RegionHeader newHeader = null;
        if (options.isoRegions()) {
            boolean anyIsoInArea = index.isoRegionFiles().keySet().stream().anyMatch(inResetArea);
            if (index.regionHeader().isPresent()) {
                RegionHeader header = index.regionHeader().get();
                List<ChunkPos> kept = header.chunks().stream().filter(inResetArea.negate()).toList();
                if (kept.size() != header.chunks().size()) {
                    newHeader = new RegionHeader(header.version(), kept);
                }
                for (Map.Entry<ChunkPos, Path> e : index.isoRegionFiles().entrySet()) {
                    if (inResetArea.test(e.getKey())) {
                        isoToDelete.put(e.getKey(), e.getValue());
                    }
                }
            } else if (anyIsoInArea) {
                warnings.add(new PlanWarning(PlanWarning.Type.ISO_HEADER_UNAVAILABLE,
                        "RegionHeader.bin missing or unreadable: iso-region files left untouched"));
            }
        }

        // Vehicles
        List<VehicleRecord> vehicles = new ArrayList<>();
        if (options.vehicles()) {
            for (VehicleRecord v : index.vehicles()) {
                if (inResetArea.test(v.storedChunk())) {
                    vehicles.add(v);
                }
            }
            boolean unreadable = index.issues().stream().anyMatch(i -> i.type() == ScanIssue.Type.DATABASE_UNREADABLE
                    && i.file() != null && i.file().getFileName().toString().equals(VehiclesDatabase.FILE_NAME));
            if (unreadable) {
                warnings.add(new PlanWarning(PlanWarning.Type.VEHICLES_UNAVAILABLE,
                        "vehicles.db could not be read: vehicles left untouched"));
            }
        }

        // Players (never modified)
        List<PlayerRecord> players = index.players().stream().filter(p -> inResetArea.test(p.chunk())).toList();

        ResetPlan draft = new ResetPlan(index, mode, options, chunksToDelete, cellsToReset, cellFiles,
                isoToDelete, newHeader, vehicles, players, List.of());

        // Warnings
        if (draft.isEmpty()) {
            warnings.add(new PlanWarning(PlanWarning.Type.NOTHING_TO_DO, "nothing would be deleted"));
        }
        if (!index.chunks().isEmpty() && chunksToDelete.size() == index.chunks().size()) {
            warnings.add(new PlanWarning(PlanWarning.Type.ENTIRE_SAVE_RESET,
                    "all " + chunksToDelete.size() + " chunks would be deleted"));
        }
        if (!players.isEmpty()) {
            warnings.add(new PlanWarning(PlanWarning.Type.PLAYERS_IN_RESET_AREA,
                    players.size() + " player(s) in the reset area"));
        }
        if (!chunksToDelete.isEmpty() && !index.globalFilesWithPositions().isEmpty()) {
            warnings.add(new PlanWarning(PlanWarning.Type.GLOBAL_FILES_NOT_CLEANED,
                    String.join(", ", index.globalFilesWithPositions())));
        }
        long problems = index.issues().stream().filter(i -> i.severity() != ScanIssue.Severity.INFO).count();
        if (problems > 0) {
            warnings.add(new PlanWarning(PlanWarning.Type.SCAN_ISSUES_PRESENT, problems + " issue(s) found by the scan"));
        }

        return new ResetPlan(index, mode, options, chunksToDelete, cellsToReset, cellFiles, isoToDelete, newHeader,
                vehicles, players, warnings);
    }
}
