package zomboidchunkreset.core.reset;

import java.nio.file.Path;
import java.util.List;

import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.testutil.FakeSave;

/**
 * Synthetic save used by the reset tests.
 *
 * <pre>
 * cell (0,0): chunks x 30..31, y 10..11 (4 chunks), zpop, chunkdata, metacell
 * cell (1,0): chunks x 32..33, y 10..11 (4 chunks), zpop, apop, chunkdata, metacell
 * cell (5,5): no chunk, zpop only
 * iso regions: (31,10) in cell (0,0) and (32,11) in cell (1,0), both listed in RegionHeader.bin
 * vehicles: id 1 in (30,10), id 2 in (33,11)
 * player: in (30,10)
 * </pre>
 */
final class ResetFixture {

    static final ChunkPos PLAYER_CHUNK = new ChunkPos(30, 10);

    private ResetFixture() {
    }

    static FakeSave create(Path dir) {
        return FakeSave.create(dir)
                .chunks(30, 10, 33, 11)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 0, 0)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 1, 0)
                .cellFile(CellFileType.ZOMBIE_POPULATION, 5, 5)
                .cellFile(CellFileType.ANIMAL_POPULATION, 1, 0)
                .cellFile(CellFileType.CHUNK_DATA, 0, 0)
                .cellFile(CellFileType.CHUNK_DATA, 1, 0)
                .cellFile(CellFileType.META_CELL, 0, 0)
                .cellFile(CellFileType.META_CELL, 1, 0)
                .file("zpop/zpop_ids.bin", new byte[6])
                .isoChunk(31, 10).isoChunk(32, 11)
                .regionHeader(List.of(new ChunkPos(31, 10), new ChunkPos(32, 11)))
                .vehicle(30, 10, 30 * 8 + 1, 10 * 8 + 1)
                .vehicle(33, 11, 33 * 8 + 1, 11 * 8 + 1)
                .networkPlayer("account1", "Character", 30 * 8 + 4, 10 * 8 + 4)
                .folder("map_visited_server")
                .file("gos_farming.bin", new byte[13]);
    }
}
