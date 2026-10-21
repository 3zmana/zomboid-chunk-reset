package zomboidchunkreset.core.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.testutil.FakeSave;
import zomboidchunkreset.core.testutil.FolderSnapshot;

class DatabasesTest {

    @TempDir
    Path dir;

    @Test
    void readsLocalAndNetworkPlayers() throws IOException {
        FakeSave save = FakeSave.create(dir)
                .localPlayer("Hero", 12345.5, 6789.25, 1543, 848)
                .networkPlayer("account1", "Character One", 100.5, 200.25);
        List<PlayerRecord> players = PlayersDatabase.read(save.root().resolve(PlayersDatabase.FILE_NAME));
        assertEquals(2, players.size());

        PlayerRecord local = players.get(0);
        assertEquals(PlayerRecord.Source.LOCAL, local.source());
        assertNull(local.accountName());
        assertEquals("Hero", local.displayName());
        assertEquals(new ChunkPos(1543, 848), local.storedChunk());
        assertEquals(new ChunkPos(1543, 848), local.chunk());

        PlayerRecord net = players.get(1);
        assertEquals(PlayerRecord.Source.NETWORK, net.source());
        assertEquals("account1", net.displayName());
        assertEquals("Character One", net.characterName());
        assertNull(net.storedChunk());
        assertEquals(new ChunkPos(12, 25), net.chunk());
    }

    @Test
    void readsVehicles() throws IOException {
        FakeSave save = FakeSave.create(dir).vehicle(1500, 300, 12004.5, 2403.75).vehicle(5, 5, 100, 100);
        List<VehicleRecord> vehicles = VehiclesDatabase.read(save.root().resolve(VehiclesDatabase.FILE_NAME));
        assertEquals(2, vehicles.size());
        assertEquals(new ChunkPos(1500, 300), vehicles.get(0).storedChunk());
        assertEquals(vehicles.get(0).storedChunk(), vehicles.get(0).chunkFromPosition());
        assertEquals(new ChunkPos(12, 12), vehicles.get(1).chunkFromPosition());
    }

    @Test
    void readingDoesNotModifyTheDatabases() throws IOException {
        FakeSave save = FakeSave.create(dir).localPlayer("Hero", 1, 1, 0, 0).vehicle(0, 0, 1, 1);
        // The game leaves empty journal files next to its databases; keep them in the test.
        Files.write(dir.resolve("players.db-journal"), new byte[0]);
        Files.write(dir.resolve("vehicles.db-journal"), new byte[0]);
        Map<String, String> before = FolderSnapshot.take(dir);
        PlayersDatabase.read(save.root().resolve(PlayersDatabase.FILE_NAME));
        VehiclesDatabase.read(save.root().resolve(VehiclesDatabase.FILE_NAME));
        assertEquals(before, FolderSnapshot.take(dir));
    }

    @Test
    void missingTablesGiveEmptyLists() throws IOException {
        Path empty = dir.resolve("empty.db");
        FakeSave.create(dir).vehicle(0, 0, 1, 1);
        // vehicles.db has no player tables
        assertEquals(List.of(), PlayersDatabase.read(dir.resolve(VehiclesDatabase.FILE_NAME)));
        Files.write(empty, new byte[0]);
        assertEquals(List.of(), VehiclesDatabase.read(empty));
    }

    @Test
    void corruptDatabaseFails() throws IOException {
        Path bad = Files.write(dir.resolve("bad.db"), "not a database at all, just text".repeat(50).getBytes());
        assertThrows(IOException.class, () -> VehiclesDatabase.read(bad));
    }
}
