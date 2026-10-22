package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileTableBuilderTest {

    @TempDir
    Path dir;

    private void write(String name, byte[] content) throws IOException {
        Files.write(dir.resolve(name), content);
    }

    @Test
    void usesTheVerifiedIdSchemeAndMergesThePatch() throws IOException {
        write("newtiledefinitions.tiles", TileDefinitionsTest.tiles(
                "blends_natural_test", 3, List.of(Map.of("FloorMaterial", "Grass_Dark"), Map.of("FloorMaterial", "Water")),
                "walls_test", 1, List.of(Map.of(), Map.of("WallW", ""))));
        write("tiledefinitions_erosion.tiles", TileDefinitionsTest.tiles(
                "e_oak_1", 2, List.of(Map.of("tree", ""), Map.of()),
                "e_newgrass_1", 1, List.of(Map.of())));
        write("jumbo_trees_big.tiles", TileDefinitionsTest.tiles("e_oakJUMBOXL_1", 1, List.of(Map.of())));
        write("jumbo_trees.tiles", TileDefinitionsTest.tiles(
                "e_oakJUMBO_1", 4, List.of(Map.of()), "e_stumps_JUMBO", 5, List.of(Map.of())));
        // The patch only adds a property: it must not erase the others.
        write(TileTableBuilder.PATCH_FILE, TileDefinitionsTest.tiles(
                "walls_test", 7, List.of(Map.of("CustomName", "x"), Map.of("CustomName", "y"))));

        TileTable t = TileTableBuilder.build(dir);
        assertEquals(TileClass.GRASS_DARK, t.classOf(2 * 1024));
        assertEquals(TileClass.WATER, t.classOf(2 * 1024 + 1));
        assertEquals(TileClass.OTHER, t.classOf(0));
        assertEquals(TileClass.WALL, t.classOf(1));
        assertEquals(TileClass.TREE, t.classOf(1_048_576 + 512));      // whole tree sheet
        assertEquals(TileClass.TREE, t.classOf(1_048_576 + 513));
        assertEquals(TileClass.VEGETATION, t.classOf(1_048_576));
        assertEquals(TileClass.TREE, t.classOf(2_097_152));
        assertEquals(TileClass.TREE, t.classOf(2_621_440 + 3 * 512));
        assertEquals(TileClass.OTHER, t.classOf(2_621_440 + 4 * 512)); // stumps
        assertEquals(TileClass.UNKNOWN, t.classOf(2 * 1024 + 2));
    }

    @Test
    void missingFilesAreReported() {
        assertThrows(IOException.class, () -> TileTableBuilder.build(dir));
    }

    @Test
    void classifierRules() {
        assertEquals(TileClass.ROAD, TileClassifier.classify("blends_street_01", Map.of("FloorMaterial", "Road_01"), false));
        assertEquals(TileClass.PAVEMENT, TileClassifier.classify("floors_exterior_street_01", Map.of("solidfloor", ""), false));
        assertEquals(TileClass.FLOOR_INTERIOR, TileClassifier.classify("floors_interior_carpet_01", Map.of("solidfloor", ""), false));
        assertEquals(TileClass.FLOOR_OTHER, TileClassifier.classify("something_01", Map.of("solidfloor", ""), false));
        assertEquals(TileClass.ROOF, TileClassifier.classify("roofs_01", Map.of(), false));
        assertEquals(TileClass.CEILING, TileClassifier.classify("ceilings_01", Map.of(), false));
        assertEquals(TileClass.WALL, TileClassifier.classify("fencing_01", Map.of("HoppableN", ""), false));
        assertEquals(TileClass.VEGETATION, TileClassifier.classify("vegetation_foliage_01", Map.of("vegitation", ""), false));
        assertEquals(TileClass.OTHER, TileClassifier.classify("furniture_01", Map.of("CustomName", "Chair"), false));
    }
}
