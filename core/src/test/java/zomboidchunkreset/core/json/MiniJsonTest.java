package zomboidchunkreset.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MiniJsonTest {

    @Test
    void parsesAllTypes() throws Exception {
        Object v = MiniJson.parse(" { \"a\": 1, \"b\": -2.5e1, \"c\": \"x\\\"y\\u00e9\\n\", \"d\": [true, false, null],"
                + " \"e\": {}, \"f\": [] } ");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(1L, m.get("a"));
        assertEquals(-25.0, m.get("b"));
        assertEquals("x\"yé\n", m.get("c"));
        List<?> d = (List<?>) m.get("d");
        assertEquals(Boolean.TRUE, d.get(0));
        assertEquals(Boolean.FALSE, d.get(1));
        assertNull(d.get(2));
        assertEquals(Map.of(), m.get("e"));
        assertEquals(List.of(), m.get("f"));
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), List.copyOf(m.keySet()));
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : new String[] {"", "{", "{\"a\" 1}", "{\"a\":1,}", "[1 2]", "\"abc", "tru", "01x",
                "{\"a\":1} extra", "{a:1}", "\"bad \\q\"", "[" .repeat(100) + "]".repeat(100)}) {
            assertThrows(MiniJson.JsonException.class, () -> MiniJson.parse(bad), bad);
        }
    }

    @Test
    void writesAndReadsBack() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "quote \" backslash \\ tab \t");
        root.put("n", 42);
        root.put("d", 1.5);
        root.put("list", List.of(Map.of("x1", 1L), Map.of("x1", -2L)));
        root.put("empty", List.of());
        String json = MiniJson.write(root);
        assertTrue(json.contains("{ \"x1\": 1 }"), json);
        Map<?, ?> back = (Map<?, ?>) MiniJson.parse(json);
        assertEquals("quote \" backslash \\ tab \t", back.get("name"));
        assertEquals(42L, back.get("n"));
        assertEquals(1.5, back.get("d"));
        assertEquals(List.of(Map.of("x1", 1L), Map.of("x1", -2L)), back.get("list"));
        assertEquals(List.of(), back.get("empty"));
    }
}
