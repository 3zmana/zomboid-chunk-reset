package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.testutil.FakeChunk;
import zomboidchunkreset.core.testutil.FakeSave;
import zomboidchunkreset.core.testutil.FolderSnapshot;

class TerrainLoaderTest {

    private static final TileTable TABLE = TileTable.of(Map.of(1, TileClass.WATER, 2, TileClass.ROAD));

    @TempDir
    Path dir;

    @Test
    void loadsInParallelWithoutChangingTheSaveAndUsesTheCache() throws Exception {
        FakeSave save = FakeSave.create(dir);
        List<ChunkPos> positions = new java.util.ArrayList<>();
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 5; y++) {
                save.chunkRaw(1500 + x, 300 + y, new FakeChunk().ground(0, 0, (x + y) % 2 == 0 ? 1 : 2).build());
                positions.add(new ChunkPos(1500 + x, 300 + y));
            }
        }
        save.chunk(1499, 299, 3); // not decodable
        positions.add(new ChunkPos(1499, 299));
        positions.add(new ChunkPos(1400, 200)); // missing file
        Map<String, String> before = FolderSnapshot.take(dir);

        TerrainLoader.Cache cache = new TerrainLoader.Cache();
        Map<ChunkPos, ChunkTerrain> result = new ConcurrentHashMap<>();
        AtomicInteger nulls = new AtomicInteger();
        int reported = TerrainLoader.load(dir, positions, TABLE, cache, 4, (pos, t) -> {
            if (t == null) {
                nulls.incrementAndGet();
            } else {
                result.put(pos, t);
            }
        }, () -> false);
        assertEquals(positions.size(), reported);
        assertEquals(30, result.size());
        assertEquals(2, nulls.get());
        assertEquals(TerrainCategory.WATER, result.get(new ChunkPos(1500, 300)).at(0, 0));
        assertEquals(TerrainCategory.ROAD, result.get(new ChunkPos(1501, 300)).at(0, 0));
        assertEquals(before, FolderSnapshot.take(dir));
        assertEquals(31, cache.size());

        // A changed chunk is read again; the others come from the cache.
        Path changed = TerrainLoader.chunkFile(dir, new ChunkPos(1500, 300));
        Files.write(changed, new FakeChunk().ground(0, 0, 2).build());
        Files.setLastModifiedTime(changed, FileTime.fromMillis(Files.getLastModifiedTime(changed).toMillis() + 5000));
        Map<ChunkPos, ChunkTerrain> second = new ConcurrentHashMap<>();
        TerrainLoader.load(dir, positions, TABLE, cache, 2, (pos, t) -> {
            if (t != null) {
                second.put(pos, t);
            }
        }, () -> false);
        assertEquals(TerrainCategory.ROAD, second.get(new ChunkPos(1500, 300)).at(0, 0));
        assertTrue(second.get(new ChunkPos(1501, 301)) == result.get(new ChunkPos(1501, 301)), "cached instance");
    }

    @Test
    void stopsWhenCancelled() throws Exception {
        FakeSave save = FakeSave.create(dir);
        List<ChunkPos> positions = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            save.chunkRaw(1500 + i, 300, new FakeChunk().ground(0, 0, 1).build());
            positions.add(new ChunkPos(1500 + i, 300));
        }
        AtomicInteger seen = new AtomicInteger();
        int reported = TerrainLoader.load(dir, positions, TABLE, new TerrainLoader.Cache(), 1,
                (pos, t) -> seen.incrementAndGet(), () -> seen.get() >= 10);
        assertEquals(10, reported);
    }

    @Test
    void anotherSaveClearsTheCache() throws Exception {
        TerrainLoader.Cache cache = new TerrainLoader.Cache();
        Path a = dir.resolve("a");
        Path b = dir.resolve("b");
        FakeSave.create(a).chunkRaw(1500, 300, new FakeChunk().ground(0, 0, 1).build());
        FakeSave.create(b).chunkRaw(1500, 300, new FakeChunk().ground(0, 0, 2).build());
        ChunkTerrain[] got = new ChunkTerrain[1];
        TerrainLoader.load(a, List.of(new ChunkPos(1500, 300)), TABLE, cache, 1, (p, t) -> got[0] = t, () -> false);
        assertNotNull(got[0]);
        TerrainLoader.load(b, List.of(new ChunkPos(1500, 300)), TABLE, cache, 1, (p, t) -> got[0] = t, () -> false);
        assertEquals(TerrainCategory.ROAD, got[0].at(0, 0));
        assertEquals(0, TerrainLoader.load(b, List.of(), TABLE, cache, 1, (p, t) -> got[0] = null, () -> false));
        assertNotNull(got[0]);
    }
}
