package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TileTableTest {

    @Test
    void roundTripsThroughItsFileFormat() throws IOException {
        Map<Integer, TileClass> m = new HashMap<>();
        for (int i = 0; i < 70_000; i++) {
            m.put(i * 3, TileClass.values()[1 + i % (TileClass.values().length - 1)]);
        }
        m.put(2_621_440, TileClass.TREE);
        TileTable table = TileTable.of(m);
        TileTable copy = TileTable.read(new ByteArrayInputStream(table.toBytes()));
        assertEquals(table, copy);
        assertEquals(TileClass.TREE, copy.classOf(2_621_440));
        assertEquals(TileClass.UNKNOWN, copy.classOf(1));
        assertEquals(TileClass.UNKNOWN, copy.classOf(-5));
        assertEquals(TileClass.UNKNOWN, copy.classOf(Integer.MAX_VALUE));
        assertEquals(m.size(), copy.size());
    }

    @Test
    void rejectsOtherFiles() {
        assertThrows(IOException.class, () -> TileTable.read(new ByteArrayInputStream(new byte[] {1, 2, 3})));
    }

    @Test
    void codesAreStable() {
        // The codes are stored in the bundled table: they must never change.
        assertEquals(0, TileClass.UNKNOWN.code());
        assertEquals(2, TileClass.WATER.code());
        assertEquals(12, TileClass.TREE.code());
        assertEquals(16, TileClass.CEILING.code());
        for (TileClass c : TileClass.values()) {
            assertEquals(c, TileClass.ofCode(c.code()));
        }
        assertEquals(TileClass.UNKNOWN, TileClass.ofCode(31));
    }

    @Test
    void bundledTableCoversTheMainTerrainTiles() {
        TileTable t = TileTable.bundled();
        assertTrue(t.size() > 50_000);
        // Ground, water, road and tree tiles seen in the reference saves.
        assertEquals(TileClass.GRASS_DARK, t.classOf(126_999));
        assertEquals(TileClass.GRASS_MEDIUM, t.classOf(126_976 + 40));
        assertTrue(t.classOf(1_048_576).code() != 0);
    }
}
