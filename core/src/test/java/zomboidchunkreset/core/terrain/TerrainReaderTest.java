package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import zomboidchunkreset.core.testutil.FakeChunk;

class TerrainReaderTest {

    private static final int GRASS = 1;
    private static final int ROAD = 2;
    private static final int TREE = 3;
    private static final int WALL = 4;
    private static final int ROOF = 5;
    private static final int CEILING = 6;
    private static final int INTERIOR = 7;
    private static final int BUSH = 8;
    private static final int WATER = 9;
    private static final int CHAIR = 10;
    private static final int MODDED = 999;

    private static final TerrainReader READER = new TerrainReader(table());

    private static TileTable table() {
        Map<Integer, TileClass> m = new HashMap<>();
        m.put(GRASS, TileClass.GRASS_MEDIUM);
        m.put(ROAD, TileClass.ROAD);
        m.put(TREE, TileClass.TREE);
        m.put(WALL, TileClass.WALL);
        m.put(ROOF, TileClass.ROOF);
        m.put(CEILING, TileClass.CEILING);
        m.put(INTERIOR, TileClass.FLOOR_INTERIOR);
        m.put(BUSH, TileClass.VEGETATION);
        m.put(WATER, TileClass.WATER);
        m.put(CHAIR, TileClass.OTHER);
        return TileTable.of(m);
    }

    @Test
    void appliesTheTopViewRules() {
        byte[] data = new FakeChunk()
                .ground(0, 0, GRASS)
                .ground(0, 1, ROAD)
                .ground(0, 2, GRASS, BUSH)
                .ground(0, 3, GRASS).tree(0, 3, TREE)
                .ground(0, 4, INTERIOR, WALL)
                .ground(0, 5, INTERIOR, CHAIR)
                .ground(0, 6, INTERIOR).object(0, 6, 1, CEILING)
                .ground(0, 7, INTERIOR).object(0, 7, 1, INTERIOR).object(0, 7, 2, ROOF)
                .ground(1, 0, MODDED)
                .ground(1, 1, WATER)
                .ground(1, 2, CHAIR)
                .ground(1, 3, ROAD).object(1, 3, 1, CHAIR)
                .build();
        ChunkTerrain t = READER.read(data).orElseThrow();
        assertTrue(t.complete());
        assertEquals(TerrainCategory.GRASS_MEDIUM, t.at(0, 0));
        assertEquals(TerrainCategory.ROAD, t.at(0, 1));
        assertEquals(TerrainCategory.TALL_GRASS, t.at(0, 2));
        assertEquals(TerrainCategory.TREE, t.at(0, 3));
        assertEquals(TerrainCategory.WALL, t.at(0, 4));
        assertEquals(TerrainCategory.INTERIOR, t.at(0, 5));
        assertEquals(TerrainCategory.ROOF_FLAT, t.at(0, 6));
        assertEquals(TerrainCategory.ROOF, t.at(0, 7));
        assertEquals(TerrainCategory.NEUTRAL, t.at(1, 0));
        assertEquals(TerrainCategory.WATER, t.at(1, 1));
        assertEquals(TerrainCategory.NEUTRAL, t.at(1, 2));
        assertEquals(TerrainCategory.ROAD, t.at(1, 3)); // furniture upstairs is not a roof
        assertEquals(TerrainCategory.NONE, t.at(5, 5)); // no square stored
    }

    @Test
    void fillsTheRestOfAPartlyDecodedChunk() {
        byte[] data = new FakeChunk().ground(0, 0, GRASS).ground(0, 1, GRASS).ground(0, 2, ROAD)
                .zombie(0, 3).ground(7, 7, WATER).build();
        ChunkTerrain t = READER.read(data).orElseThrow();
        assertFalse(t.complete());
        assertEquals(TerrainCategory.ROAD, t.at(0, 2));
        assertEquals(TerrainCategory.GRASS_MEDIUM, t.at(7, 7)); // not reached: most common category
        assertEquals(TerrainCategory.GRASS_MEDIUM, t.dominant());
    }

    @Test
    void ignoresPartialResultsOfUnverifiedVersions() {
        byte[] partial = new FakeChunk().worldVersion(250).ground(0, 0, GRASS).zombie(0, 1).build();
        assertTrue(READER.read(partial).isEmpty());
        byte[] complete = new FakeChunk().worldVersion(250).ground(0, 0, GRASS).build();
        assertEquals(TerrainCategory.GRASS_MEDIUM, READER.read(complete).orElseThrow().at(0, 0));
    }

    @Test
    void emptyWhenNothingCanBeRead() {
        assertTrue(READER.read(new byte[40]).isEmpty());
        assertTrue(READER.read(new FakeChunk().zombie(0, 0).build()).isEmpty());
    }
}
