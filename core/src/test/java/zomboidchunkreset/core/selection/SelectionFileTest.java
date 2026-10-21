package zomboidchunkreset.core.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.reset.ResetMode;

class SelectionFileTest {

    @TempDir
    Path dir;

    @Test
    void aRectangleIsWrittenAsOneRectangle() throws IOException {
        ChunkSelection s = ChunkSelection.ofRect(new ChunkBounds(1500, 300, 1511, 308));
        assertEquals(List.of(new ChunkBounds(1500, 300, 1511, 308)), SelectionFile.rectangles(s));
        Path f = dir.resolve("base.json");
        SelectionFile.write(f, s, ResetMode.KEEP_SELECTED);
        String text = Files.readString(f);
        assertTrue(text.contains("\"format\": \"zomboid-chunk-reset-selection\""), text);
        assertTrue(text.contains("\"inGame\": \"X 12000-12095, Y 2400-2471\""), text);
        SelectionFile.Content c = SelectionFile.read(f);
        assertEquals(s, c.selection());
        assertEquals(Optional.of(ResetMode.KEEP_SELECTED), c.mode());
    }

    @Test
    void randomSelectionsSurviveARoundTrip() throws IOException {
        Random random = new Random(7);
        for (int round = 0; round < 20; round++) {
            ChunkSelection s = new ChunkSelection();
            for (int i = 0; i < 15; i++) {
                int x = random.nextInt(200) - 100;
                int y = random.nextInt(200) - 100;
                ChunkBounds r = new ChunkBounds(x, y, x + random.nextInt(40), y + random.nextInt(40));
                if (random.nextBoolean()) {
                    s.addRect(r);
                } else {
                    s.invertWithin(r);
                }
            }
            List<ChunkBounds> rects = SelectionFile.rectangles(s);
            ChunkSelection rebuilt = new ChunkSelection();
            int total = 0;
            for (ChunkBounds r : rects) {
                rebuilt.addRect(r);
                total += r.width() * r.height();
            }
            assertEquals(s, rebuilt);
            assertEquals(s.size(), total, "rectangles must not overlap");
            assertEquals(s, SelectionFile.parse(SelectionFile.toJson(s, null)).selection());
        }
    }

    @Test
    void readsHandWrittenInGameCoordinates() throws IOException {
        String json = """
                ﻿{
                  "format": "zomboid-chunk-reset-selection",
                  "version": 1,
                  "squares": [ { "x1": 12005, "y1": 2410, "x2": 12090, "y2": 2470 } ]
                }
                """;
        SelectionFile.Content c = SelectionFile.parse(json);
        assertEquals(ChunkSelection.ofRect(ChunkBounds.of(ChunkPos.ofSquare(12005, 2410), ChunkPos.ofSquare(12090, 2470))),
                c.selection());
        assertEquals(Optional.empty(), c.mode());
        assertEquals(12 * 8, c.selection().size());
    }

    @Test
    void emptySelection() throws IOException {
        SelectionFile.Content c = SelectionFile.parse(SelectionFile.toJson(new ChunkSelection(), ResetMode.DELETE_SELECTED));
        assertTrue(c.selection().isEmpty());
        assertEquals(Optional.of(ResetMode.DELETE_SELECTED), c.mode());
    }

    @Test
    void rejectsInvalidFiles() {
        String[] bad = {
            "not json",
            "[]",
            "{\"format\": \"something else\", \"version\": 1}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 2}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"mode\": \"EXPLODE\"}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"chunks\": {}}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"chunks\": [1]}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"chunks\": [{\"x1\": 1, \"y1\": 1, \"x2\": 2}]}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"chunks\": [{\"x1\": 1.5, \"y1\": 1, \"x2\": 2, \"y2\": 2}]}",
            "{\"format\": \"zomboid-chunk-reset-selection\", \"version\": 1, \"squares\": [{\"x1\": 0, \"y1\": 0, \"x2\": 99999999, \"y2\": 1}]}",
        };
        for (String json : bad) {
            assertThrows(SelectionFile.InvalidSelectionFileException.class, () -> SelectionFile.parse(json), json);
        }
    }
}
