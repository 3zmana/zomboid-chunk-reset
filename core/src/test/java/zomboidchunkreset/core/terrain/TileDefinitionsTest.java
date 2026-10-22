package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TileDefinitionsTest {

    /** Writes a .tiles file in the verified layout. Sheets: name, number, then tiles as property maps. */
    static byte[] tiles(Object... sheets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("tdef".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le(1));
        out.writeBytes(le(sheets.length / 3));
        for (int i = 0; i < sheets.length; i += 3) {
            String name = (String) sheets[i];
            int number = (Integer) sheets[i + 1];
            @SuppressWarnings("unchecked")
            List<Map<String, String>> tiles = (List<Map<String, String>>) sheets[i + 2];
            out.writeBytes((name + "\n" + name + ".png\n").getBytes(StandardCharsets.UTF_8));
            out.writeBytes(le(8));
            out.writeBytes(le(Math.max(1, tiles.size() / 8)));
            out.writeBytes(le(number));
            out.writeBytes(le(tiles.size()));
            for (Map<String, String> props : tiles) {
                out.writeBytes(le(props.size()));
                props.forEach((k, v) -> out.writeBytes((k + "\n" + v + "\n").getBytes(StandardCharsets.UTF_8)));
            }
        }
        return out.toByteArray();
    }

    private static byte[] le(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    @Test
    void parsesSheetsTilesAndProperties() {
        byte[] data = tiles("blends_test_01", 5, List.of(Map.of("FloorMaterial", "Sand", "solidfloor", ""), Map.of()),
                "walls_test_01", 9, List.of(Map.of("WallN", "")));
        List<TileDefinitions.Sheet> sheets = TileDefinitions.parse(data);
        assertEquals(2, sheets.size());
        assertEquals("blends_test_01", sheets.get(0).name());
        assertEquals("blends_test_01.png", sheets.get(0).imageName());
        assertEquals(5, sheets.get(0).number());
        assertEquals("Sand", sheets.get(0).tiles().get(0).get("FloorMaterial"));
        assertEquals("", sheets.get(0).tiles().get(0).get("solidfloor"));
        assertEquals(Map.of(), sheets.get(0).tiles().get(1));
        assertEquals(9, sheets.get(1).number());
    }

    @Test
    void rejectsBrokenFiles() {
        byte[] data = tiles("a", 1, List.of(Map.of("k", "v")));
        assertThrows(IllegalArgumentException.class, () -> TileDefinitions.parse(new byte[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
                () -> TileDefinitions.parse(java.util.Arrays.copyOf(data, data.length - 2)));
        assertThrows(IllegalArgumentException.class,
                () -> TileDefinitions.parse(java.util.Arrays.copyOf(data, data.length + 1)));
    }
}
