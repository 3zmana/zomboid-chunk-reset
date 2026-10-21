package zomboidchunkreset.core.reset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.selection.ChunkSelection;
import zomboidchunkreset.core.testutil.FolderSnapshot;

class ResetExecutorTest {

    @TempDir
    Path dir;

    private ResetPlan keepCell00(ResetOptions options) throws IOException {
        ResetFixture.create(dir);
        return ResetPlanner.plan(SaveScanner.scan(dir), ChunkSelection.ofRect(new ChunkBounds(30, 10, 31, 11)),
                ResetMode.KEEP_SELECTED, options);
    }

    private static List<Long> vehicleIds(SaveIndex index) {
        return index.vehicles().stream().map(VehicleRecord::id).sorted().toList();
    }

    @Test
    void simulationChangesNothingAndPredictsTheRealRun() throws IOException {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        Map<String, String> before = FolderSnapshot.take(dir);

        ResetReport sim = ResetExecutor.run(plan, ResetExecutor.Mode.SIMULATE, ResetProgress.NONE);
        assertEquals(before, FolderSnapshot.take(dir));
        assertEquals(ResetReport.Outcome.COMPLETED, sim.outcome());
        assertFalse(sim.changesMade());
        assertEquals(4, sim.chunkFilesDeleted());
        assertEquals(2, sim.columnFoldersRemoved()); // map/32 and map/33
        assertEquals(5, sim.cellFilesDeleted());
        assertEquals(1, sim.isoFilesDeleted());
        assertTrue(sim.regionHeaderRewritten());
        assertEquals(1, sim.vehiclesDeleted());

        ResetReport real = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.COMPLETED, real.outcome(), real.message());
        assertTrue(real.changesMade());
        assertEquals(sim.chunkFilesDeleted(), real.chunkFilesDeleted());
        assertEquals(sim.chunkBytesDeleted(), real.chunkBytesDeleted());
        assertEquals(sim.columnFoldersRemoved(), real.columnFoldersRemoved());
        assertEquals(sim.cellFilesDeleted(), real.cellFilesDeleted());
        assertEquals(sim.isoFilesDeleted(), real.isoFilesDeleted());
        assertEquals(sim.vehiclesDeleted(), real.vehiclesDeleted());
    }

    @Test
    void realRunLeavesAConsistentSave() throws IOException {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        byte[] playersBefore = Files.readAllBytes(dir.resolve("players.db"));

        ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.COMPLETED, report.outcome(), report.message());

        SaveIndex after = SaveScanner.scan(dir);
        assertEquals(List.of(), after.issues());
        assertEquals(Set.of(new ChunkPos(30, 10), new ChunkPos(30, 11), new ChunkPos(31, 10), new ChunkPos(31, 11)),
                after.chunks().keySet());
        assertEquals(Set.of(new CellPos(0, 0)), after.cellFiles(CellFileType.ZOMBIE_POPULATION).keySet());
        assertEquals(Set.of(), after.cellFiles(CellFileType.ANIMAL_POPULATION).keySet());
        assertEquals(Set.of(new CellPos(0, 0)), after.cellFiles(CellFileType.CHUNK_DATA).keySet());
        assertEquals(Set.of(new CellPos(0, 0)), after.cellFiles(CellFileType.META_CELL).keySet());
        assertEquals(Set.of(new ChunkPos(31, 10)), after.isoRegionFiles().keySet());
        assertEquals(List.of(new ChunkPos(31, 10)), after.regionHeader().orElseThrow().chunks());
        assertEquals(List.of(1L), vehicleIds(after));
        assertEquals(1, after.players().size());
        assertArrayEquals(playersBefore, Files.readAllBytes(dir.resolve("players.db")));
        assertTrue(Files.exists(dir.resolve("zpop/zpop_ids.bin")));
        assertFalse(Files.exists(dir.resolve("map/32")));
        assertFalse(Files.exists(dir.resolve("isoregiondata/RegionHeader.bin" + AtomicFiles.TEMP_SUFFIX)));
    }

    @Test
    void resettingEverythingWritesAnEmptyRegionHeader() throws IOException {
        ResetFixture.create(dir);
        ResetPlan plan = ResetPlanner.plan(SaveScanner.scan(dir), new ChunkSelection(), ResetMode.KEEP_SELECTED,
                ResetOptions.ALL);
        ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.COMPLETED, report.outcome(), report.message());

        SaveIndex after = SaveScanner.scan(dir);
        assertTrue(after.chunks().isEmpty());
        assertEquals(new RegionHeader(249, List.of()), after.regionHeader().orElseThrow());
        assertEquals(8, Files.size(dir.resolve("isoregiondata/RegionHeader.bin")));
        assertTrue(after.vehicles().isEmpty());
        for (CellFileType type : CellFileType.values()) {
            assertTrue(after.cellFiles(type).isEmpty(), type.toString());
        }
        assertEquals(List.of(), after.issues());
        try (Stream<Path> map = Files.list(dir.resolve("map"))) {
            assertEquals(0, map.count());
        }
    }

    @Test
    void abortsWhenTheSaveChangedSinceTheScan() throws IOException {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        Files.delete(dir.resolve("map/33/11.bin"));
        Map<String, String> before = FolderSnapshot.take(dir);

        for (ResetExecutor.Mode mode : ResetExecutor.Mode.values()) {
            ResetReport report = ResetExecutor.run(plan, mode, ResetProgress.NONE);
            assertEquals(ResetReport.Outcome.ABORTED, report.outcome());
            assertFalse(report.changesMade());
            assertTrue(report.message().contains("changed since it was scanned"), report.message());
        }
        assertEquals(before, FolderSnapshot.take(dir));
    }

    @Test
    void abortsWhenTheRegionHeaderChanged() throws IOException {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        Files.write(dir.resolve("isoregiondata/RegionHeader.bin"),
                new RegionHeader(249, List.of(new ChunkPos(31, 10))).toBytes());
        Map<String, String> before = FolderSnapshot.take(dir);
        ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.ABORTED, report.outcome());
        assertEquals(before, FolderSnapshot.take(dir));
    }

    @Test
    void abortsWithoutChangesWhenTheVehicleDatabaseIsLocked() throws Exception {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        try (Connection game = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("vehicles.db"));
             Statement st = game.createStatement()) {
            st.execute("BEGIN EXCLUSIVE");
            Map<String, String> before = FolderSnapshot.take(dir);
            ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
            assertEquals(ResetReport.Outcome.ABORTED, report.outcome());
            assertFalse(report.changesMade());
            assertTrue(report.message().contains("locked"), report.message());
            assertEquals(before, FolderSnapshot.take(dir));
            st.execute("ROLLBACK");
        }
    }

    @Test
    void cancellingPartWayLeavesAConsistentSave() throws Exception {
        ResetFixture.create(dir);
        // Delete both cells' chunks; cancel after the header, the two iso files and 2 chunk files.
        ResetPlan plan = ResetPlanner.plan(SaveScanner.scan(dir), ChunkSelection.of(List.of(new ChunkPos(0, 0))),
                ResetMode.KEEP_SELECTED, ResetOptions.ALL);
        AtomicReference<ResetReport> result = new AtomicReference<>();
        Thread t = new Thread(() -> result.set(ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, (done, total) -> {
            if (done == 5) {
                Thread.currentThread().interrupt();
            }
        })));
        t.start();
        t.join();
        ResetReport report = result.get();
        assertEquals(ResetReport.Outcome.CANCELLED, report.outcome());
        assertTrue(report.changesMade());
        assertEquals(2, report.chunkFilesDeleted());

        SaveIndex after = SaveScanner.scan(dir);
        assertEquals(List.of(), after.issues());
        assertEquals(6, after.chunks().size());
        // Cells still holding chunks keep their files; cell (5,5), which has no chunk, is left for a later run.
        assertEquals(Set.of(new CellPos(0, 0), new CellPos(1, 0), new CellPos(5, 5)),
                after.cellFiles(CellFileType.ZOMBIE_POPULATION).keySet());
        assertEquals(Set.of(new CellPos(0, 0), new CellPos(1, 0)), after.cellFiles(CellFileType.META_CELL).keySet());
        // Vehicle 1 sat in (30,10), deleted first; vehicle 2 sits in (33,11), still there.
        assertEquals(List.of(2L), vehicleIds(after));
    }

    @Test
    void anErrorPartWayStopsAndLeavesAConsistentSave() throws IOException {
        ResetFixture.create(dir);
        ResetPlan plan = ResetPlanner.plan(SaveScanner.scan(dir), ChunkSelection.ofRect(new ChunkBounds(30, 10, 31, 11)),
                ResetMode.KEEP_SELECTED, ResetOptions.ALL);
        // Replace a planned chunk file by a non-empty folder: it cannot be deleted as a file.
        Path blocker = dir.resolve("map/33/10.bin");
        Files.delete(blocker);
        Files.createDirectories(blocker.resolve("inside"));

        ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.STOPPED, report.outcome());
        assertTrue(report.changesMade());

        // Restore a normal layout to rescan, then check consistency.
        Files.delete(blocker.resolve("inside"));
        Files.delete(blocker);
        SaveIndex after = SaveScanner.scan(dir);
        assertEquals(List.of(), after.issues());
        // Cell (1,0) still has (33,11): its files and vehicle stay. Cell (5,5) is left alone after a stop.
        assertTrue(after.hasChunk(new ChunkPos(33, 11)));
        assertTrue(after.cellFiles(CellFileType.ZOMBIE_POPULATION).containsKey(new CellPos(5, 5)));
        assertTrue(after.cellFiles(CellFileType.META_CELL).containsKey(new CellPos(1, 0)));
        assertTrue(after.cellFiles(CellFileType.ANIMAL_POPULATION).containsKey(new CellPos(1, 0)));
        assertEquals(List.of(1L, 2L), vehicleIds(after));
    }

    @Test
    void reportsProgressUpToTheTotal() throws IOException {
        ResetPlan plan = keepCell00(ResetOptions.ALL);
        int[] last = {0, 0};
        ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, (done, total) -> {
            last[0] = done;
            last[1] = total;
        });
        // 1 header + 1 iso + 4 chunks + 5 cell files + 1 vehicle step
        assertEquals(12, last[1]);
        assertEquals(12, last[0]);
    }

    @Test
    void anEmptyPlanDoesNothing() throws IOException {
        ResetFixture.create(dir);
        ResetPlan plan = ResetPlanner.plan(SaveScanner.scan(dir), new ChunkSelection(), ResetMode.DELETE_SELECTED,
                ResetOptions.ALL);
        Map<String, String> before = FolderSnapshot.take(dir);
        ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
        assertEquals(ResetReport.Outcome.COMPLETED, report.outcome());
        assertFalse(report.changesMade());
        assertEquals(before, FolderSnapshot.take(dir));
    }
}
