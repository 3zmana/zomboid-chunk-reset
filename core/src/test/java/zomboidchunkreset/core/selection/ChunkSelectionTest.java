package zomboidchunkreset.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;

class ChunkSelectionTest {

    @Test
    void addRemoveAndRectangles() {
        ChunkSelection s = new ChunkSelection();
        assertTrue(s.isEmpty());
        assertTrue(s.add(new ChunkPos(1, 1)));
        assertFalse(s.add(new ChunkPos(1, 1)));
        s.addRect(new ChunkBounds(0, 0, 2, 2));
        assertEquals(9, s.size());
        s.removeRect(new ChunkBounds(1, 1, 2, 2));
        assertEquals(5, s.size());
        assertFalse(s.contains(new ChunkPos(1, 1)));
        assertTrue(s.remove(new ChunkPos(0, 0)));
        assertFalse(s.remove(new ChunkPos(0, 0)));
        assertEquals(List.of(new ChunkPos(0, 1), new ChunkPos(0, 2), new ChunkPos(1, 0), new ChunkPos(2, 0)),
                List.copyOf(s.positions()));
        s.clear();
        assertTrue(s.isEmpty());
    }

    @Test
    void invertWithinOnlyTouchesTheRectangle() {
        ChunkSelection s = ChunkSelection.of(List.of(new ChunkPos(0, 0), new ChunkPos(10, 10)));
        s.invertWithin(new ChunkBounds(0, 0, 1, 1));
        assertEquals(List.of(new ChunkPos(0, 1), new ChunkPos(1, 0), new ChunkPos(1, 1), new ChunkPos(10, 10)),
                List.copyOf(s.positions()));
    }

    @Test
    void rectanglesAcrossCellsAndNegativeCoordinates() {
        ChunkBounds rect = new ChunkBounds(-40, -3, 70, 33); // spans 5 x 3 cells
        ChunkSelection s = ChunkSelection.ofRect(rect);
        assertEquals(111 * 37, s.size());
        assertTrue(s.contains(new ChunkPos(-40, -3)));
        assertTrue(s.contains(new ChunkPos(70, 33)));
        assertTrue(s.contains(-1, 0));
        assertFalse(s.contains(new ChunkPos(-41, 0)));
        assertFalse(s.contains(new ChunkPos(71, 0)));
        assertFalse(s.contains(new ChunkPos(0, 34)));
        assertEquals(rect, s.bounds().orElseThrow());
        assertEquals(s.size(), s.countByCell().values().stream().mapToInt(Integer::intValue).sum());
        int[] seen = {0};
        s.forEach(p -> {
            assertTrue(rect.contains(p), p.toString());
            seen[0]++;
        });
        assertEquals(s.size(), seen[0]);

        s.removeRect(new ChunkBounds(-40, -3, 70, 33));
        assertTrue(s.isEmpty());
        assertTrue(s.bounds().isEmpty());
        assertTrue(s.countByCell().isEmpty());
    }

    @Test
    void wholeCellRowsAndInversionCounts() {
        ChunkSelection s = ChunkSelection.ofRect(new ChunkBounds(0, 0, 31, 31));
        assertEquals(1024, s.size());
        assertEquals(1024, s.countByCell().get(new CellPos(0, 0)));
        s.invertWithin(new ChunkBounds(16, 0, 47, 31));
        assertEquals(16 * 32 + 16 * 32, s.size());
        assertTrue(s.contains(new ChunkPos(15, 5)));
        assertFalse(s.contains(new ChunkPos(16, 5)));
        assertTrue(s.contains(new ChunkPos(47, 31)));
        s.invertWithin(new ChunkBounds(16, 0, 47, 31));
        assertEquals(ChunkSelection.ofRect(new ChunkBounds(0, 0, 31, 31)), s);
    }

    @Test
    void matchesASimpleSetOnRandomOperations() {
        java.util.Random random = new java.util.Random(42);
        ChunkSelection s = new ChunkSelection();
        java.util.Set<ChunkPos> reference = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            int x0 = random.nextInt(140) - 70;
            int y0 = random.nextInt(140) - 70;
            ChunkBounds r = new ChunkBounds(x0, y0, x0 + random.nextInt(45), y0 + random.nextInt(45));
            int op = random.nextInt(4);
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int y = r.minY(); y <= r.maxY(); y++) {
                    ChunkPos p = new ChunkPos(x, y);
                    switch (op) {
                        case 0 -> reference.add(p);
                        case 1 -> reference.remove(p);
                        case 2 -> {
                            if (!reference.remove(p)) {
                                reference.add(p);
                            }
                        }
                        default -> { }
                    }
                }
            }
            switch (op) {
                case 0 -> s.addRect(r);
                case 1 -> s.removeRect(r);
                case 2 -> s.invertWithin(r);
                default -> {
                    ChunkPos p = new ChunkPos(r.minX(), r.minY());
                    assertEquals(reference.add(p), s.add(p));
                }
            }
            assertEquals(reference.size(), s.size());
        }
        assertEquals(new java.util.TreeSet<>(reference), s.positions());
    }

    @Test
    void countsByCellAndCopies() {
        ChunkSelection s = ChunkSelection.ofRect(new ChunkBounds(30, 0, 33, 0));
        assertEquals(Map.of(new CellPos(0, 0), 2, new CellPos(1, 0), 2), s.countByCell());
        ChunkSelection copy = s.copy();
        assertEquals(s, copy);
        copy.add(new ChunkPos(-5, -5));
        assertEquals(4, s.size());
        assertEquals(1, copy.countByCell().get(new CellPos(-1, -1)));
    }
}
