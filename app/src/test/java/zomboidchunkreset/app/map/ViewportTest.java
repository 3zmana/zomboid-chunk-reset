package zomboidchunkreset.app.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;

class ViewportTest {

    private static final double EPS = 1e-9;

    @Test
    void mapsPixelsToChunksAndBack() {
        Viewport v = new Viewport();
        v.fit(new ChunkBounds(100, 200, 199, 299), 1000, 1000, 0); // 100 x 100 chunks in 1000 px
        assertEquals(10, v.scale(), EPS);
        assertEquals(new ChunkPos(100, 200), v.chunkAt(0.5, 0.5));
        assertEquals(new ChunkPos(199, 299), v.chunkAt(999, 999));
        assertEquals(new ChunkPos(150, 250), v.chunkAt(505, 505));
        assertEquals(500, v.screenX(150), EPS);
        assertEquals(500, v.screenY(250), EPS);
    }

    @Test
    void zoomKeepsThePointUnderTheCursor() {
        Viewport v = new Viewport();
        v.fit(new ChunkBounds(0, 0, 99, 99), 800, 600, 10);
        double cx = v.chunkX(321);
        double cy = v.chunkY(123);
        v.zoomAt(2.5, 321, 123);
        assertEquals(cx, v.chunkX(321), 1e-6);
        assertEquals(cy, v.chunkY(123), 1e-6);
    }

    @Test
    void zoomIsClamped() {
        Viewport v = new Viewport();
        v.zoomAt(1e9, 0, 0);
        assertEquals(Viewport.MAX_SCALE, v.scale(), EPS);
        v.zoomAt(1e-12, 0, 0);
        assertEquals(Viewport.MIN_SCALE, v.scale(), EPS);
    }

    @Test
    void panningMovesByScreenDistance() {
        Viewport v = new Viewport();
        v.setScale(4, 400, 400);
        double x = v.originX();
        double y = v.originY();
        v.panBy(40, -20); // drag the map right and up
        assertEquals(x - 10, v.originX(), EPS);
        assertEquals(y + 5, v.originY(), EPS);
    }

    @Test
    void centerOn() {
        Viewport v = new Viewport();
        v.setScale(8, 800, 600);
        v.centerOn(1543.5, 848.5, 800, 600);
        assertEquals(new ChunkPos(1543, 848), v.chunkAt(400, 300));
    }
}
