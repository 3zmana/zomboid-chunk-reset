package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.testutil.FolderSnapshot;

/**
 * Checks the chunk content decoder and the tile table against real saves (copies only), when
 * {@code ZCR_REAL_SAVES} is set; skipped otherwise. Only generic invariants are asserted.
 */
class RealSaveTerrainTest {

    private static final String ENV = "ZCR_REAL_SAVES";

    @TestFactory
    Stream<DynamicTest> realChunksDecode() {
        String value = System.getenv(ENV);
        Assumptions.assumeTrue(value != null && !value.isBlank(), ENV + " is not set: real-save tests skipped");
        List<Path> saves = Arrays.stream(value.split(File.pathSeparator))
                .filter(s -> !s.isBlank()).map(Path::of).toList();
        return saves.stream().map(save -> DynamicTest.dynamicTest("save #" + (saves.indexOf(save) + 1),
                () -> check(save)));
    }

    private static void check(Path root) throws Exception {
        Map<String, String> before = FolderSnapshot.take(root);
        SaveIndex index = SaveScanner.scan(root);
        TileTable table = TileTable.bundled();
        TerrainReader terrain = new TerrainReader(table);

        int total = 0;
        int complete = 0;
        long groundSquares = 0;
        long groundKnown = 0;
        long groundFloor = 0;
        Map<String, Integer> failures = new TreeMap<>();
        Map<TerrainCategory, Long> categories = new EnumMap<>(TerrainCategory.class);
        long start = System.nanoTime();
        for (ChunkEntry e : index.chunks().values()) {
            Path file = root.resolve("map").resolve(Integer.toString(e.pos().x()))
                    .resolve(e.pos().y() + ".bin");
            byte[] data = Files.readAllBytes(file);
            total++;
            long[] counts = new long[3];
            try {
                ChunkContentReader.read(data, (x, y, level, sprites, count) -> {
                    if (level == 0 && count > 0) {
                        counts[0]++;
                        TileClass c = table.classOf(sprites[0]);
                        if (c != TileClass.UNKNOWN) {
                            counts[1]++;
                        }
                        if (c.isFloor()) {
                            counts[2]++;
                        }
                    }
                });
                complete++;
            } catch (ChunkContentException ex) {
                failures.merge(ex.getMessage(), 1, Integer::sum);
            }
            groundSquares += counts[0];
            groundKnown += counts[1];
            groundFloor += counts[2];
            ChunkTerrain t = terrain.read(data).orElseThrow(() -> new AssertionError("no terrain for " + e.pos()));
            for (int x = 0; x < ChunkTerrain.SIDE; x++) {
                for (int y = 0; y < ChunkTerrain.SIDE; y++) {
                    categories.merge(t.at(x, y), 1L, Long::sum);
                }
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("chunks %d, fully decoded %d, failures %s, ground squares %d, known %d, floors %d, %d ms%n",
                total, complete, failures, groundSquares, groundKnown, groundFloor, millis);
        System.out.println("categories " + categories);

        // Reading must not change anything.
        assertEquals(before, FolderSnapshot.take(root), "the save was modified");
        assertTrue(complete >= total * 0.99, "too many chunks not fully decoded: " + failures);
        assertTrue(groundKnown >= groundSquares * 0.999, "too many unknown ground tiles");
        assertTrue(groundFloor >= groundSquares * 0.99, "lowest objects are not floors");
    }
}
