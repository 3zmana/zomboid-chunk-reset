package zomboidchunkreset.core.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.testutil.FakeSave;
import zomboidchunkreset.core.testutil.FolderSnapshot;

class SaveScannerTest {

    @TempDir
    Path dir;

    private static List<ScanIssue.Type> types(SaveIndex index) {
        return index.issues().stream().map(ScanIssue::type).toList();
    }

    /** A small but complete server-like save spanning two cells. */
    private FakeSave serverSave() {
        return FakeSave.create(dir)
                .chunks(30, 10, 33, 11) // cells (0,0) and (1,0)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 0, 0)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 1, 0)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 5, 5) // zpop also exists for cells without chunks
                .cellFile(CellFileType.CHUNK_DATA, 0, 0)
                .cellFile(CellFileType.META_CELL, 0, 0)
                .cellFile(CellFileType.META_CELL, 1, 0)
                .cellFile(CellFileType.ANIMAL_POPULATION, 1, 0)
                .file("zpop/zpop_ids.bin", new byte[6])
                .file("zpop/zpop_virtual.bin", new byte[6])
                .isoChunk(31, 10).isoChunk(32, 11)
                .regionHeader(List.of(new ChunkPos(31, 10), new ChunkPos(32, 11)))
                .networkPlayer("account1", "Character", 30 * 8 + 1.5, 10 * 8 + 2.5)
                .vehicle(33, 11, 33 * 8 + 4, 11 * 8 + 4)
                .folder("map_visited_server")
                .file("servermap_symbols.bin", new byte[17])
                .file("gos_farming.bin", new byte[13]);
    }

    @Test
    void scansAServerSave() throws IOException {
        FakeSave save = serverSave();
        SaveIndex index = SaveScanner.scan(save.root());

        assertEquals(SaveKind.MULTIPLAYER_SERVER, index.kind());
        assertTrue(index.kindSignals().contains("map_visited_server"));
        assertTrue(index.kindSignals().contains("players.db:networkPlayers"));

        assertEquals(8, index.chunks().size());
        assertEquals(new ChunkBounds(30, 10, 33, 11), index.bounds().orElseThrow());
        assertTrue(index.hasChunk(new ChunkPos(33, 11)));
        assertFalse(index.hasChunk(new ChunkPos(34, 11)));
        assertEquals(8L * (17 + 32), index.totalChunkBytes());
        assertEquals(Set.of(FakeSave.WORLD_VERSION), index.chunkWorldVersions());
        assertNotNull(index.chunks().get(new ChunkPos(30, 10)).header());
        assertEquals(Map.of(new CellPos(0, 0), 4, new CellPos(1, 0), 4), index.chunkCountByCell());

        assertEquals(Set.of(new CellPos(0, 0), new CellPos(1, 0), new CellPos(5, 5)),
                index.cellFiles(CellFileType.ZOMBIE_POPULATION).keySet());
        assertEquals(Set.of(new CellPos(0, 0)), index.cellFiles(CellFileType.CHUNK_DATA).keySet());
        assertEquals(Set.of(new CellPos(1, 0)), index.cellFiles(CellFileType.ANIMAL_POPULATION).keySet());

        assertEquals(Set.of(new ChunkPos(31, 10), new ChunkPos(32, 11)), index.isoRegionFiles().keySet());
        assertEquals(2, index.regionHeader().orElseThrow().chunks().size());

        assertEquals(1, index.players().size());
        assertEquals(new ChunkPos(30, 10), index.players().get(0).chunk());
        assertEquals(1, index.vehicles().size());
        assertEquals(List.of("gos_farming.bin"), index.globalFilesWithPositions());
        assertEquals(List.of(), index.issues());
    }

    @Test
    void scanDoesNotModifyAnything() throws IOException {
        FakeSave save = serverSave();
        Map<String, String> before = FolderSnapshot.take(save.root());
        SaveScanner.scan(save.root());
        assertEquals(before, FolderSnapshot.take(save.root()));
    }

    @Test
    void detectsASinglePlayerSave() throws IOException {
        FakeSave save = FakeSave.create(dir).chunk(1543, 848)
                .localPlayer("Hero", 12345.5, 6789.25, 1543, 848)
                .file("map_visited.bin", new byte[4]).file("map_ver.bin", new byte[4]).file("mods.txt", new byte[1]);
        SaveIndex index = SaveScanner.scan(save.root());
        assertEquals(SaveKind.SINGLE_PLAYER, index.kind());
        assertEquals(List.of(), index.issues());
    }

    @Test
    void unknownKindWhenNoSignalOrContradictorySignals() throws IOException {
        FakeSave save = FakeSave.create(dir).chunk(0, 0);
        assertEquals(SaveKind.UNKNOWN, SaveScanner.scan(save.root()).kind());
        save.folder("map_visited_server").file("map_visited.bin", new byte[1]);
        assertEquals(SaveKind.UNKNOWN, SaveScanner.scan(save.root()).kind());
    }

    @Test
    void reportsInconsistencies() throws IOException {
        FakeSave save = FakeSave.create(dir)
                .chunk(1, 1)
                .chunkRaw(1, 2, new ChunkHeaderBytes(999).bytes) // declared length != size
                .chunkRaw(1, 3, new byte[5]) // shorter than a header
                .file("map/1/notes.txt", new byte[1])
                .file("map/readme.txt", new byte[1])
                .isoChunk(1, 1).isoChunk(1, 2)
                .regionHeader(List.of(new ChunkPos(1, 1), new ChunkPos(9, 9))) // 9,9 missing; 1,2 not listed
                .file("isoregiondata/datachunk_4_4.bin", new byte[] {0, 0, 0, 92, 0, 0, 0, (byte) 249, 0, 0, 0, 5,
                        0, 0, 0, 4}) // header says chunk (5,4) and 92 bytes, file is (4,4) and 16 bytes
                .vehicle(7, 7, 1, 1) // stored chunk (7,7) but position in (0,0)
                .localPlayer("Hero", 100, 100, 3, 3); // stored (3,3) but position in (12,12)
        SaveIndex index = SaveScanner.scan(save.root());

        List<ScanIssue.Type> types = types(index);
        assertTrue(types.contains(ScanIssue.Type.CHUNK_LENGTH_MISMATCH), types::toString);
        assertTrue(types.contains(ScanIssue.Type.CHUNK_UNREADABLE), types::toString);
        assertEquals(2, types.stream().filter(t -> t == ScanIssue.Type.MAP_UNEXPECTED_ENTRY).count(), types::toString);
        assertTrue(types.contains(ScanIssue.Type.ISO_HEADER_ENTRY_WITHOUT_FILE), types::toString);
        assertEquals(2, types.stream().filter(t -> t == ScanIssue.Type.ISO_FILE_NOT_IN_HEADER).count(), types::toString);
        assertTrue(types.contains(ScanIssue.Type.ISO_FILE_HEADER_MISMATCH), types::toString);
        assertTrue(types.contains(ScanIssue.Type.VEHICLE_CHUNK_MISMATCH), types::toString);
        assertTrue(types.contains(ScanIssue.Type.PLAYER_CHUNK_MISMATCH), types::toString);

        // Unreadable chunks are still listed, without a header.
        assertEquals(3, index.chunks().size());
        assertNull(index.chunks().get(new ChunkPos(1, 3)).header());
    }

    @Test
    void listingOnlySkipsHeaders() throws IOException {
        FakeSave save = FakeSave.create(dir).chunkRaw(1, 2, new byte[3]);
        SaveIndex index = SaveScanner.scan(save.root(), ScanOptions.LISTING_ONLY, ScanProgress.NONE);
        assertEquals(1, index.chunks().size());
        assertNull(index.chunks().get(new ChunkPos(1, 2)).header());
        assertEquals(List.of(), index.issues());
        assertTrue(index.chunkWorldVersions().isEmpty());
    }

    @Test
    void reportsProgressPerColumn() throws IOException {
        FakeSave save = FakeSave.create(dir).chunks(0, 0, 2, 1);
        List<String> calls = new ArrayList<>();
        SaveScanner.scan(save.root(), ScanOptions.FULL, (done, total) -> calls.add(done + "/" + total));
        assertEquals(List.of("0/3", "1/3", "2/3", "3/3"), calls);
    }

    @Test
    void canBeCancelledByInterrupting() throws Exception {
        FakeSave save = FakeSave.create(dir).chunks(0, 0, 3, 3);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                SaveScanner.scan(save.root());
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        t.start();
        t.join();
        assertTrue(thrown.get() instanceof InterruptedIOException, String.valueOf(thrown.get()));
    }

    @Test
    void emptySaveHasNoBounds() throws IOException {
        SaveIndex index = SaveScanner.scan(FakeSave.create(dir).root());
        assertTrue(index.bounds().isEmpty());
        assertTrue(index.chunks().isEmpty());
        assertTrue(index.regionHeader().isEmpty());
    }

    @Test
    void rejectsFoldersThatAreNotSaves() {
        assertThrows(NotASaveException.class, () -> SaveScanner.scan(dir));
        assertThrows(NotASaveException.class, () -> SaveScanner.scan(dir.resolve("missing")));
    }

    @Test
    void chunkFileNameParsing() {
        assertEquals(464, SaveScanner.parseChunkFileName("464.bin").orElseThrow());
        assertEquals(-3, SaveScanner.parseChunkFileName("-3.bin").orElseThrow());
        assertTrue(SaveScanner.parseChunkFileName("464.bak").isEmpty());
        assertTrue(SaveScanner.parseChunkFileName("x.bin").isEmpty());
    }

    /** A chunk file of 17 + 3 bytes whose header declares a different length. */
    private record ChunkHeaderBytes(byte[] bytes) {
        ChunkHeaderBytes(int declaredLength) {
            this(withHeader(declaredLength));
        }

        private static byte[] withHeader(int declaredLength) {
            byte[] content = new byte[20];
            byte[] h = new zomboidchunkreset.core.format.ChunkFileHeader(0, 249, declaredLength, 0).toBytes();
            System.arraycopy(h, 0, content, 0, h.length);
            return content;
        }
    }
}
