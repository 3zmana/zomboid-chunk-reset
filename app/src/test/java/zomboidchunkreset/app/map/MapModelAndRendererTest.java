package zomboidchunkreset.app.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.selection.ChunkSelection;
import zomboidchunkreset.core.testutil.FakeSave;

class MapModelAndRendererTest {

    @TempDir
    Path dir;

    private MapModel model() throws IOException {
        FakeSave save = FakeSave.create(dir)
                .chunk(40, 10, 10)     // small file
                .chunk(41, 10, 5000)   // big file
                .chunk(70, 40)         // another cell
                .networkPlayer("account1", "Character", 40 * 8 + 4, 10 * 8 + 4);
        return MapModel.of(SaveScanner.scan(save.root()));
    }

    @Test
    void regionIsCellAlignedWithAMargin() throws IOException {
        MapModel m = model();
        // Chunks span cells (1,0) to (2,1): region covers cells (0,-1) to (3,2).
        assertEquals(new ChunkBounds(0, -32, 127, 95), m.region().orElseThrow());
        assertEquals(new ChunkBounds(40, 10, 70, 40), m.dataBounds().orElseThrow());
        assertTrue(m.inRegion(0, -32));
        assertFalse(m.inRegion(-1, 0));
    }

    @Test
    void levelsFollowFileSize() throws IOException {
        MapModel m = model();
        assertEquals(1, m.level(40, 10));
        assertEquals(MapModel.LEVELS, m.level(41, 10));
        assertTrue(m.hasChunk(70, 40));
        assertFalse(m.hasChunk(42, 10));
        assertEquals(0, m.level(-500, -500));
    }

    @Test
    void playersAreInChunkUnits() throws IOException {
        MapModel.PlayerMarker p = model().players().get(0);
        assertEquals("account1", p.name());
        assertEquals(40.5, p.chunkX(), 1e-9);
        assertEquals(10.5, p.chunkY(), 1e-9);
    }

    @Test
    void emptySave() throws IOException {
        MapModel m = MapModel.of(SaveScanner.scan(FakeSave.create(dir).root()));
        assertTrue(m.isEmpty());
        assertTrue(m.region().isEmpty());
        assertEquals(Palette.OUTSIDE, MapRenderer.colorOf(0, 0, m, new ChunkSelection(), ResetMode.DELETE_SELECTED));
    }

    @Test
    void coloursShowWhatWillHappen() throws IOException {
        MapModel m = model();
        ChunkSelection sel = ChunkSelection.of(List.of(new ChunkPos(40, 10), new ChunkPos(42, 10)));
        int level = m.level(40, 10);

        // Delete mode: selected chunks are deleted, others kept.
        assertEquals(Palette.deleted(level), MapRenderer.colorOf(40, 10, m, sel, ResetMode.DELETE_SELECTED));
        assertEquals(Palette.kept(m.level(41, 10)), MapRenderer.colorOf(41, 10, m, sel, ResetMode.DELETE_SELECTED));
        assertEquals(Palette.SELECTED_EMPTY_DELETE, MapRenderer.colorOf(42, 10, m, sel, ResetMode.DELETE_SELECTED));

        // Keep mode: selected chunks are protected, the others deleted.
        assertEquals(Palette.protectedChunk(level), MapRenderer.colorOf(40, 10, m, sel, ResetMode.KEEP_SELECTED));
        assertEquals(Palette.deleted(m.level(41, 10)), MapRenderer.colorOf(41, 10, m, sel, ResetMode.KEEP_SELECTED));
        assertEquals(Palette.SELECTED_EMPTY_KEEP, MapRenderer.colorOf(42, 10, m, sel, ResetMode.KEEP_SELECTED));

        // Never-generated chunks alternate by cell; outside the region is dark.
        assertNotEquals(MapRenderer.colorOf(0, 0, m, sel, ResetMode.DELETE_SELECTED),
                MapRenderer.colorOf(32, 0, m, sel, ResetMode.DELETE_SELECTED));
        assertEquals(Palette.OUTSIDE, MapRenderer.colorOf(-1, 0, m, sel, ResetMode.DELETE_SELECTED));
    }

    @Test
    void rendersTheBufferPixelPerChunk() throws IOException {
        MapModel m = model();
        Viewport v = new Viewport();
        v.setScale(4, 40, 40);
        v.centerOn(41, 11, 40, 40); // 10 x 10 chunks visible: x 36..45, y 6..15
        int[] buffer = new int[40 * 40];
        ChunkSelection sel = ChunkSelection.of(List.of(new ChunkPos(40, 10)));
        MapRenderer.render(buffer, 40, 40, 1, v, m, sel, ResetMode.DELETE_SELECTED);
        int px = (int) v.screenX(40) + 1;
        int py = (int) v.screenY(10) + 1;
        assertEquals(Palette.deleted(m.level(40, 10)), buffer[py * 40 + px]);
        int px2 = (int) v.screenX(41) + 2;
        assertEquals(Palette.kept(m.level(41, 10)), buffer[py * 40 + px2]);

        // Same picture at a 2x screen scale: twice the pixels.
        int[] hiDpi = new int[80 * 80];
        MapRenderer.render(hiDpi, 80, 80, 2, v, m, sel, ResetMode.DELETE_SELECTED);
        assertEquals(buffer[py * 40 + px], hiDpi[(py * 2) * 80 + px * 2]);
        assertEquals(buffer[py * 40 + px2], hiDpi[(py * 2 + 1) * 80 + px2 * 2 + 1]);
    }

    @Test
    void zoomedOutRenderingSamplesManyChunksPerPixel() throws IOException {
        MapModel m = model();
        Viewport v = new Viewport();
        v.fit(m.region().orElseThrow(), 16, 16, 0);
        int[] buffer = new int[16 * 16];
        MapRenderer.render(buffer, 16, 16, 1, v, m, new ChunkSelection(), ResetMode.KEEP_SELECTED);
        for (int c : buffer) {
            assertEquals(0xFF, c >>> 24, "opaque");
        }
    }
}
