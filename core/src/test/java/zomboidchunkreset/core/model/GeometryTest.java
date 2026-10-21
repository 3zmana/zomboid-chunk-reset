package zomboidchunkreset.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void sizes() {
        assertEquals(8, WorldGeometry.CHUNK_SIZE_IN_SQUARES);
        assertEquals(32, WorldGeometry.CELL_SIZE_IN_CHUNKS);
        assertEquals(256, WorldGeometry.CELL_SIZE_IN_SQUARES);
    }

    @Test
    void squareToChunkUsesFloorDivision() {
        assertEquals(1543, WorldGeometry.squareToChunk(12345.6));
        assertEquals(1543, WorldGeometry.squareToChunk(12344));
        assertEquals(1542, WorldGeometry.squareToChunk(12343));
        assertEquals(-1, WorldGeometry.squareToChunk(-1));
        assertEquals(-1, WorldGeometry.squareToChunk(-0.5));
        assertEquals(-2, WorldGeometry.squareToChunk(-9));
    }

    @Test
    void chunkToCell() {
        assertEquals(48, WorldGeometry.chunkToCell(1536));
        assertEquals(48, WorldGeometry.chunkToCell(1567));
        assertEquals(49, WorldGeometry.chunkToCell(1568));
        assertEquals(-1, WorldGeometry.chunkToCell(-1));
        assertEquals(48, WorldGeometry.squareToCell(12345));
    }

    @Test
    void chunkPosFromSquaresAndBack() {
        ChunkPos c = ChunkPos.ofSquare(12345, 6789);
        assertEquals(new ChunkPos(1543, 848), c);
        assertEquals(12344, c.minSquareX());
        assertEquals(12351, c.maxSquareX());
        assertEquals(6784, c.minSquareY());
        assertEquals(6791, c.maxSquareY());
        assertEquals(new CellPos(48, 26), c.cell());
        assertTrue(new CellPos(48, 26).contains(c));
        assertFalse(new CellPos(48, 27).contains(c));
    }

    @Test
    void keyRoundTripIncludingNegatives() {
        for (ChunkPos p : new ChunkPos[] {new ChunkPos(0, 0), new ChunkPos(1543, 848), new ChunkPos(-3, 7),
                new ChunkPos(5, -9), new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE)}) {
            assertEquals(p, ChunkPos.fromKey(p.toKey()));
        }
    }

    @Test
    void ordering() {
        assertTrue(new ChunkPos(1, 9).compareTo(new ChunkPos(2, 0)) < 0);
        assertTrue(new ChunkPos(1, 1).compareTo(new ChunkPos(1, 2)) < 0);
    }

    @Test
    void bounds() {
        ChunkBounds b = ChunkBounds.of(new ChunkPos(5, 2), new ChunkPos(1, 4));
        assertEquals(new ChunkBounds(1, 2, 5, 4), b);
        assertEquals(5, b.width());
        assertEquals(3, b.height());
        assertTrue(b.contains(new ChunkPos(1, 2)));
        assertTrue(b.contains(new ChunkPos(5, 4)));
        assertFalse(b.contains(new ChunkPos(6, 4)));
        assertEquals(new ChunkBounds(-1, 2, 5, 4), b.including(new ChunkPos(-1, 3)));
        assertThrows(IllegalArgumentException.class, () -> new ChunkBounds(2, 0, 1, 0));
    }
}
