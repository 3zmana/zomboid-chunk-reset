package zomboidchunkreset.core.reset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.selection.ChunkSelection;
import zomboidchunkreset.core.testutil.FolderSnapshot;

/**
 * Runs real resets on <b>temporary copies</b> of real saves and checks that the result is consistent.
 * Like {@code RealSaveIntegrationTest}, it only runs when {@code ZCR_REAL_SAVES} is set, and it
 * asserts generic properties only (the protected area is derived from the first player's position).
 */
class RealSaveResetTest {

    private static final String ENV = "ZCR_REAL_SAVES";

    @TestFactory
    Stream<DynamicTest> resetsOnCopiesOfRealSaves() {
        String value = System.getenv(ENV);
        Assumptions.assumeTrue(value != null && !value.isBlank(), ENV + " is not set: real-save tests skipped");
        List<Path> saves = Arrays.stream(value.split(File.pathSeparator))
                .filter(s -> !s.isBlank()).map(Path::of).toList();
        return saves.stream().flatMap(save -> {
            String name = "save #" + (saves.indexOf(save) + 1);
            return Stream.of(
                    DynamicTest.dynamicTest(name + ": keep a base, reset the rest", () -> keepBase(save)),
                    DynamicTest.dynamicTest(name + ": delete one whole cell", () -> deleteOneCell(save)));
        });
    }

    /** Keeps a 12 x 9 chunk area around the first player (like a base) and resets everything else. */
    private static void keepBase(Path original) throws IOException {
        Map<String, String> originalBefore = FolderSnapshot.take(original);
        Path copy = copyOf(original);
        try {
            SaveIndex before = SaveScanner.scan(copy);
            ChunkPos center = before.players().get(0).chunk();
            ChunkBounds base = new ChunkBounds(center.x() - 5, center.y() - 4, center.x() + 6, center.y() + 4);
            ChunkSelection protectedArea = ChunkSelection.ofRect(base);
            ResetPlan plan = ResetPlanner.plan(before, protectedArea, ResetMode.KEEP_SELECTED, ResetOptions.ALL);
            byte[] playersBefore = Files.readAllBytes(copy.resolve("players.db"));

            Map<String, String> copyBefore = FolderSnapshot.take(copy);
            ResetReport sim = ResetExecutor.run(plan, ResetExecutor.Mode.SIMULATE, ResetProgress.NONE);
            assertEquals(ResetReport.Outcome.COMPLETED, sim.outcome(), sim.message());
            assertEquals(copyBefore, FolderSnapshot.take(copy), "the simulation changed files");

            long start = System.nanoTime();
            ResetReport real = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(ResetReport.Outcome.COMPLETED, real.outcome(), real.message());
            assertEquals(sim.chunkFilesDeleted(), real.chunkFilesDeleted());
            assertEquals(sim.cellFilesDeleted(), real.cellFilesDeleted());
            assertEquals(sim.isoFilesDeleted(), real.isoFilesDeleted());
            assertEquals(sim.vehiclesDeleted(), real.vehiclesDeleted());
            assertEquals(sim.columnFoldersRemoved(), real.columnFoldersRemoved());

            SaveIndex after = SaveScanner.scan(copy);
            assertEquals(List.of(), after.issues(), "the reset left inconsistencies");

            // Exactly the protected chunks remain.
            Set<ChunkPos> expected = before.chunks().keySet().stream().filter(base::contains).collect(Collectors.toSet());
            assertEquals(expected, after.chunks().keySet());
            for (VehicleRecord v : after.vehicles()) {
                assertTrue(base.contains(v.storedChunk()), "vehicle left outside the base");
            }
            assertEquals(before.vehicles().stream().filter(v -> base.contains(v.storedChunk())).count(),
                    after.vehicles().size());
            for (ChunkPos pos : after.isoRegionFiles().keySet()) {
                assertTrue(base.contains(pos));
            }

            // Per-cell files: kept exactly for cells that still have chunks, gone elsewhere
            // (except zpop of chunk-less cells touching the protected area, which stay).
            Set<CellPos> cellsLeft = after.chunkCountByCell().keySet();
            for (CellFileType type : CellFileType.values()) {
                Set<CellPos> expectedCells = new HashSet<>();
                for (CellPos cell : before.cellFiles(type).keySet()) {
                    boolean touchesBase = protectedArea.countByCell().containsKey(cell);
                    if (cellsLeft.contains(cell) || (touchesBase && !before.chunkCountByCell().containsKey(cell))) {
                        expectedCells.add(cell);
                    }
                }
                assertEquals(expectedCells, after.cellFiles(type).keySet(), type.toString());
            }

            // Players are never touched.
            assertArrayEquals(playersBefore, Files.readAllBytes(copy.resolve("players.db")));
            assertEquals(before.players().size(), after.players().size());

            System.out.printf("%s keep-base: kept %d of %d chunks; deleted %d chunk files (%.1f MB), %d cell files, "
                            + "%d iso files, %d vehicles, %d empty folders in %d ms; players in reset area: %d%n",
                    before.kind(), after.chunks().size(), before.chunks().size(), real.chunkFilesDeleted(),
                    real.chunkBytesDeleted() / 1e6, real.cellFilesDeleted(), real.isoFilesDeleted(),
                    real.vehiclesDeleted(), real.columnFoldersRemoved(), millis, plan.playersInResetArea().size());
        } finally {
            deleteRecursively(copy);
        }
        assertEquals(originalBefore, FolderSnapshot.take(original), "the original save was modified");
    }

    /** Deletes every chunk of one cell that has chunks but no player. */
    private static void deleteOneCell(Path original) throws IOException {
        Map<String, String> originalBefore = FolderSnapshot.take(original);
        Path copy = copyOf(original);
        try {
            SaveIndex before = SaveScanner.scan(copy);
            Set<CellPos> playerCells = before.players().stream().map(PlayerRecord::chunk).map(ChunkPos::cell)
                    .collect(Collectors.toSet());
            CellPos target = before.chunkCountByCell().entrySet().stream()
                    .filter(e -> !playerCells.contains(e.getKey()))
                    .max(Map.Entry.<CellPos, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).orElse(null);
            Assumptions.assumeTrue(target != null, "every cell holds a player");
            ChunkBounds cellArea = new ChunkBounds(target.minChunkX(), target.minChunkY(),
                    target.minChunkX() + 31, target.minChunkY() + 31);

            ResetPlan plan = ResetPlanner.plan(before, ChunkSelection.ofRect(cellArea), ResetMode.DELETE_SELECTED,
                    ResetOptions.ALL);
            assertEquals(Set.of(target), plan.cellsToReset());
            ResetReport real = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, ResetProgress.NONE);
            assertEquals(ResetReport.Outcome.COMPLETED, real.outcome(), real.message());

            SaveIndex after = SaveScanner.scan(copy);
            assertEquals(List.of(), after.issues());
            assertFalse(after.chunkCountByCell().containsKey(target));
            Map<CellPos, Integer> otherCellsBefore = before.chunkCountByCell();
            otherCellsBefore.remove(target);
            assertEquals(otherCellsBefore, after.chunkCountByCell());
            for (CellFileType type : CellFileType.values()) {
                assertFalse(after.cellFiles(type).containsKey(target), type.toString());
                Set<CellPos> others = new HashSet<>(before.cellFiles(type).keySet());
                others.remove(target);
                assertEquals(others, after.cellFiles(type).keySet(), type.toString());
            }
            System.out.printf("%s delete-cell %s: deleted %d chunk files, %d cell files, %d iso files, %d vehicles%n",
                    before.kind(), target, real.chunkFilesDeleted(), real.cellFilesDeleted(), real.isoFilesDeleted(),
                    real.vehiclesDeleted());
        } finally {
            deleteRecursively(copy);
        }
        assertEquals(originalBefore, FolderSnapshot.take(original), "the original save was modified");
    }

    private static Path copyOf(Path original) throws IOException {
        Path target = Files.createTempDirectory("zcr-real-save-");
        try (Stream<Path> paths = Files.walk(original)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                Path dest = target.resolve(original.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return target;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}
