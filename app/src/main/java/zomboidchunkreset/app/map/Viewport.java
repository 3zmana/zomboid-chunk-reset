package zomboidchunkreset.app.map;

import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;

/**
 * Maps screen pixels (logical, as used by JavaFX layouts) to chunk coordinates and back.
 * Chunk (x, y) covers the screen rectangle starting at {@code screenX(x), screenY(y)} with side
 * {@link #scale()} pixels. No JavaFX dependency, so it can be unit-tested.
 */
public final class Viewport {

    /** Smallest zoom: pixels per chunk. */
    public static final double MIN_SCALE = 0.05;

    /** Largest zoom: pixels per chunk. */
    public static final double MAX_SCALE = 64;

    private double scale = 1;
    private double originX;
    private double originY;

    /** Pixels per chunk. */
    public double scale() {
        return scale;
    }

    /** Chunk X coordinate shown at the left edge. */
    public double originX() {
        return originX;
    }

    /** Chunk Y coordinate shown at the top edge. */
    public double originY() {
        return originY;
    }

    /** Fractional chunk X under a screen X. */
    public double chunkX(double px) {
        return originX + px / scale;
    }

    /** Fractional chunk Y under a screen Y. */
    public double chunkY(double py) {
        return originY + py / scale;
    }

    /** The chunk under a screen point. */
    public ChunkPos chunkAt(double px, double py) {
        return new ChunkPos((int) Math.floor(chunkX(px)), (int) Math.floor(chunkY(py)));
    }

    /** Screen X of a (fractional) chunk X coordinate. */
    public double screenX(double chunkX) {
        return (chunkX - originX) * scale;
    }

    /** Screen Y of a (fractional) chunk Y coordinate. */
    public double screenY(double chunkY) {
        return (chunkY - originY) * scale;
    }

    /** Multiplies the zoom by {@code factor}, keeping the point under the cursor in place. */
    public void zoomAt(double factor, double px, double py) {
        double cx = chunkX(px);
        double cy = chunkY(py);
        scale = clamp(scale * factor);
        originX = cx - px / scale;
        originY = cy - py / scale;
    }

    /** Moves the view by a screen distance (dragging the map by dx, dy). */
    public void panBy(double dx, double dy) {
        originX -= dx / scale;
        originY -= dy / scale;
    }

    /** Zooms and centres so that {@code bounds} fills a view of the given size, with a margin. */
    public void fit(ChunkBounds bounds, double width, double height, double marginPx) {
        double usableW = Math.max(1, width - 2 * marginPx);
        double usableH = Math.max(1, height - 2 * marginPx);
        scale = clamp(Math.min(usableW / bounds.width(), usableH / bounds.height()));
        centerOn(bounds.minX() + bounds.width() / 2.0, bounds.minY() + bounds.height() / 2.0, width, height);
    }

    /** Centres the view on a (fractional) chunk position. */
    public void centerOn(double chunkX, double chunkY, double width, double height) {
        originX = chunkX - width / 2 / scale;
        originY = chunkY - height / 2 / scale;
    }

    /** Sets the zoom directly (clamped), keeping the centre of a view of the given size. */
    public void setScale(double newScale, double width, double height) {
        zoomAt(newScale / scale, width / 2, height / 2);
    }

    private static double clamp(double s) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }
}
