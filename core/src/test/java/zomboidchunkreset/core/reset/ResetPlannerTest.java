package zomboidchunkreset.core.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.selection.ChunkSelection;

class ResetPlannerTest {

    @TempDir
    Path dir;

    private SaveIndex scan() throws IOException {
        return SaveScanner.scan(ResetFixture.create(dir).root());
    }

    private static Set<ChunkPos> chunks(ResetPlan plan) {
        return Set.copyOf(plan.chunksToDelete().stream().map(ChunkEntry::pos).toList());
    }

    private static Set<CellPos> cells(ResetPlan plan, CellFileType type) {
        return plan.cellFilesToDelete(type).keySet();
    }

    @Test
    void keepingAWholeCellResetsTheOthers() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.ofRect(new ChunkBounds(30, 10, 31, 11)),
                ResetMode.KEEP_SELECTED, ResetOptions.ALL);

        assertEquals(Set.of(new ChunkPos(32, 10), new ChunkPos(32, 11), new ChunkPos(33, 10), new ChunkPos(33, 11)),
                chunks(plan));
        // Cell (1,0) lost all its chunks; cell (5,5) has none and lies entirely outside the protected area.
        assertEquals(Set.of(new CellPos(1, 0), new CellPos(5, 5)), plan.cellsToReset());
        assertEquals(Set.of(new CellPos(1, 0), new CellPos(5, 5)), cells(plan, CellFileType.ZOMBIE_POPULATION));
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.ANIMAL_POPULATION));
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.CHUNK_DATA));
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.META_CELL));

        assertEquals(Set.of(new ChunkPos(32, 11)), plan.isoFilesToDelete().keySet());
        assertEquals(List.of(new ChunkPos(31, 10)), plan.newRegionHeader().orElseThrow().chunks());
        assertEquals(List.of(2L), plan.vehiclesToDelete().stream().map(VehicleRecord::id).toList());
        assertTrue(plan.playersInResetArea().isEmpty());

        assertEquals(4 + 5 + 1, plan.filesToDelete());
        assertTrue(plan.hasWarning(PlanWarning.Type.GLOBAL_FILES_NOT_CLEANED));
        assertFalse(plan.hasWarning(PlanWarning.Type.PLAYERS_IN_RESET_AREA));
        assertFalse(plan.hasWarning(PlanWarning.Type.ENTIRE_SAVE_RESET));
        assertFalse(plan.isEmpty());
    }

    @Test
    void aCellThatKeepsOneChunkKeepsItsFiles() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.of(List.of(ResetFixture.PLAYER_CHUNK)),
                ResetMode.KEEP_SELECTED, ResetOptions.ALL);
        assertEquals(7, plan.chunksToDelete().size());
        assertEquals(Set.of(new CellPos(1, 0), new CellPos(5, 5)), plan.cellsToReset());
        assertFalse(cells(plan, CellFileType.CHUNK_DATA).contains(new CellPos(0, 0)));
        assertFalse(cells(plan, CellFileType.ZOMBIE_POPULATION).contains(new CellPos(0, 0)));
        // Both iso-region chunks are outside the kept chunk.
        assertEquals(List.of(), plan.newRegionHeader().orElseThrow().chunks());
    }

    @Test
    void deletingTheChunksOfACellResetsIt() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.ofRect(new ChunkBounds(32, 10, 33, 11)),
                ResetMode.DELETE_SELECTED, ResetOptions.ALL);
        assertEquals(4, plan.chunksToDelete().size());
        // Cell (5,5) is not selected: its zombie population stays.
        assertEquals(Set.of(new CellPos(1, 0)), plan.cellsToReset());
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.ZOMBIE_POPULATION));
    }

    @Test
    void partialDeletionInACellKeepsItsFiles() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.of(List.of(new ChunkPos(32, 10))),
                ResetMode.DELETE_SELECTED, ResetOptions.ALL);
        assertEquals(1, plan.chunksToDelete().size());
        assertTrue(plan.cellsToReset().isEmpty());
        assertEquals(0, plan.cellFileCount());
        assertTrue(plan.newRegionHeader().isEmpty());
        assertTrue(plan.vehiclesToDelete().isEmpty());
    }

    @Test
    void selectingAWholeEmptyCellResetsItsPopulation() throws IOException {
        CellPos cell = new CellPos(5, 5);
        ChunkBounds wholeCell = new ChunkBounds(cell.minChunkX(), cell.minChunkY(), cell.minChunkX() + 31,
                cell.minChunkY() + 31);
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.ofRect(wholeCell), ResetMode.DELETE_SELECTED,
                ResetOptions.ALL);
        assertTrue(plan.chunksToDelete().isEmpty());
        assertEquals(Set.of(cell), cells(plan, CellFileType.ZOMBIE_POPULATION));

        ChunkSelection almost = ChunkSelection.ofRect(wholeCell);
        almost.remove(new ChunkPos(cell.minChunkX(), cell.minChunkY()));
        ResetPlan partial = ResetPlanner.plan(scan(), almost, ResetMode.DELETE_SELECTED, ResetOptions.ALL);
        assertTrue(partial.isEmpty());
    }

    @Test
    void optionsLimitWhatIsDeleted() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), ChunkSelection.ofRect(new ChunkBounds(30, 10, 31, 11)),
                ResetMode.KEEP_SELECTED, ResetOptions.CHUNKS_ONLY);
        assertEquals(4, plan.chunksToDelete().size());
        assertTrue(cells(plan, CellFileType.ZOMBIE_POPULATION).isEmpty());
        assertTrue(cells(plan, CellFileType.ANIMAL_POPULATION).isEmpty());
        // Technical per-cell files always follow the cell.
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.CHUNK_DATA));
        assertEquals(Set.of(new CellPos(1, 0)), cells(plan, CellFileType.META_CELL));
        assertTrue(plan.isoFilesToDelete().isEmpty());
        assertTrue(plan.newRegionHeader().isEmpty());
        assertTrue(plan.vehiclesToDelete().isEmpty());
    }

    @Test
    void keepingNothingResetsEverythingWithWarnings() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), new ChunkSelection(), ResetMode.KEEP_SELECTED, ResetOptions.ALL);
        assertEquals(8, plan.chunksToDelete().size());
        assertTrue(plan.hasWarning(PlanWarning.Type.ENTIRE_SAVE_RESET));
        assertTrue(plan.hasWarning(PlanWarning.Type.PLAYERS_IN_RESET_AREA));
        assertEquals(1, plan.playersInResetArea().size());
        assertEquals(2, plan.vehiclesToDelete().size());
        assertEquals(Set.of(new CellPos(0, 0), new CellPos(1, 0), new CellPos(5, 5)), plan.cellsToReset());
    }

    @Test
    void deletingNothingIsReported() throws IOException {
        ResetPlan plan = ResetPlanner.plan(scan(), new ChunkSelection(), ResetMode.DELETE_SELECTED, ResetOptions.ALL);
        assertTrue(plan.isEmpty());
        assertTrue(plan.hasWarning(PlanWarning.Type.NOTHING_TO_DO));
        assertFalse(plan.hasWarning(PlanWarning.Type.GLOBAL_FILES_NOT_CLEANED));
    }

    @Test
    void unreadableRegionHeaderLeavesIsoRegionsAlone() throws IOException {
        ResetFixture.create(dir).file("isoregiondata/RegionHeader.bin", new byte[] {1, 2, 3});
        SaveIndex index = SaveScanner.scan(dir);
        ResetPlan plan = ResetPlanner.plan(index, new ChunkSelection(), ResetMode.KEEP_SELECTED, ResetOptions.ALL);
        assertTrue(plan.isoFilesToDelete().isEmpty());
        assertTrue(plan.newRegionHeader().isEmpty());
        assertTrue(plan.hasWarning(PlanWarning.Type.ISO_HEADER_UNAVAILABLE));
        assertTrue(plan.hasWarning(PlanWarning.Type.SCAN_ISSUES_PRESENT));
    }

    @Test
    void thePlanDoesNotFollowLaterSelectionChanges() throws IOException {
        ChunkSelection selection = ChunkSelection.of(List.of(new ChunkPos(32, 10)));
        ResetPlan plan = ResetPlanner.plan(scan(), selection, ResetMode.DELETE_SELECTED, ResetOptions.ALL);
        selection.addRect(new ChunkBounds(30, 10, 33, 11));
        assertEquals(1, plan.chunksToDelete().size());
    }
}
