package zomboidchunkreset.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.testutil.FolderSnapshot;

/**
 * Checks the format assumptions of this program against real saves.
 *
 * <p>Runs only when the environment variable {@code ZCR_REAL_SAVES} lists one or more save folders,
 * separated by the platform path separator ({@code ;} on Windows, {@code :} elsewhere). Use copies
 * of your saves. Real saves are never committed to the repository: they contain player names.
 *
 * <p>Only generic invariants are asserted, so the test works with any valid Build 42 save.
 */
class RealSaveIntegrationTest {

    private static final String ENV = "ZCR_REAL_SAVES";

    @TestFactory
    Stream<DynamicTest> realSavesMatchTheVerifiedFormat() {
        String value = System.getenv(ENV);
        Assumptions.assumeTrue(value != null && !value.isBlank(), ENV + " is not set: real-save tests skipped");
        List<Path> saves = Arrays.stream(value.split(File.pathSeparator))
                .filter(s -> !s.isBlank()).map(Path::of).toList();
        return saves.stream().map(save -> DynamicTest.dynamicTest("save #" + (saves.indexOf(save) + 1),
                () -> checkSave(save)));
    }

    private static void checkSave(Path root) throws Exception {
        assertTrue(Files.isDirectory(root), "not a folder: " + root);
        Map<String, String> before = FolderSnapshot.take(root);

        long start = System.nanoTime();
        SaveIndex index = SaveScanner.scan(root);
        long millis = (System.nanoTime() - start) / 1_000_000;

        // Scanning must not change a single file.
        assertEquals(before, FolderSnapshot.take(root), "the scan modified the save");

        // No inconsistency of any kind.
        assertEquals(List.of(), index.issues(), "issues found");

        // Kind of save detected from content.
        assertNotEquals(SaveKind.UNKNOWN, index.kind(), "signals: " + index.kindSignals());

        // Chunk files: every header readable, flag 0, declared length = file size (checked by the scan).
        assertFalse(index.chunks().isEmpty(), "no chunks");
        for (ChunkEntry e : index.chunks().values()) {
            assertNotNull(e.header(), e.pos().toString());
            assertEquals(0, e.header().flag(), e.pos().toString());
        }

        // A cell holds at most 32 x 32 chunks.
        int maxPerCell = index.chunkCountByCell().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(maxPerCell <= WorldGeometry.CELL_SIZE_IN_CHUNKS * WorldGeometry.CELL_SIZE_IN_CHUNKS,
                "cell with " + maxPerCell + " chunks");

        // Every vehicle sits in a saved chunk, and its stored chunk is floor(position / 8).
        for (VehicleRecord v : index.vehicles()) {
            assertEquals(v.chunkFromPosition(), v.storedChunk(), "vehicle " + v.id());
            assertTrue(index.hasChunk(v.storedChunk()), "vehicle " + v.id() + " outside saved chunks");
        }

        // Every player sits in a saved chunk; single-player records store floor(position / 8).
        assertFalse(index.players().isEmpty(), "no players");
        for (PlayerRecord p : index.players()) {
            assertTrue(index.hasChunk(p.chunk()), "a player is outside saved chunks");
            if (p.storedChunk() != null) {
                assertEquals(p.chunk(), p.storedChunk());
            }
        }

        // Iso regions: header and files list exactly the same chunks, all of them saved chunks.
        Set<ChunkPos> isoFiles = index.isoRegionFiles().keySet();
        if (index.regionHeader().isPresent()) {
            assertEquals(isoFiles, new HashSet<>(index.regionHeader().get().chunks()));
        }
        for (ChunkPos pos : isoFiles) {
            assertTrue(index.hasChunk(pos), "iso region data for unsaved " + pos);
        }

        // chunkdata, apop and metacell files only exist for cells that have saved chunks.
        // (zpop files may exist for cells without chunks.) The deletion rules rely on this.
        Set<CellPos> cellsWithChunks = index.chunkCountByCell().keySet();
        for (CellFileType type : List.of(CellFileType.CHUNK_DATA, CellFileType.ANIMAL_POPULATION,
                CellFileType.META_CELL)) {
            for (CellPos cell : index.cellFiles(type).keySet()) {
                assertTrue(cellsWithChunks.contains(cell), type + " file for a cell without chunks: " + cell);
            }
        }

        // Summary (no personal data: counts only).
        System.out.printf("%s: %d chunks (%.1f MB) in %d cells, bounds %s, world versions %s, "
                        + "%d iso-region chunks, cell files zpop=%d apop=%d chunkdata=%d metacell=%d, "
                        + "%d players, %d vehicles, global files with positions %s, scanned in %d ms%n",
                index.kind(), index.chunks().size(), index.totalChunkBytes() / 1e6, cellsWithChunks.size(),
                index.bounds().orElseThrow(), index.chunkWorldVersions(), isoFiles.size(),
                index.cellFiles(CellFileType.ZOMBIE_POPULATION).size(),
                index.cellFiles(CellFileType.ANIMAL_POPULATION).size(),
                index.cellFiles(CellFileType.CHUNK_DATA).size(),
                index.cellFiles(CellFileType.META_CELL).size(),
                index.players().size(), index.vehicles().size(), index.globalFilesWithPositions(), millis);
    }
}
