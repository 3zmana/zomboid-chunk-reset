package zomboidchunkreset.app.map;

import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.selection.ChunkSelection;

/**
 * Paints the chunk grid into an ARGB pixel buffer. Each chunk's colour is looked up once per frame
 * (rows and columns falling in the same chunk are copied), so the cost is bounded by the number of
 * pixels even when the whole map is visible. No JavaFX dependency.
 */
public final class MapRenderer {

    private MapRenderer() {
    }

    /**
     * @param argb       destination, {@code width * height} pixels, row by row
     * @param width      buffer width in physical pixels
     * @param height     buffer height in physical pixels
     * @param pixelRatio physical pixels per logical pixel (screen scaling, e.g. 1.5 on a 150% display)
     */
    public static void render(int[] argb, int width, int height, double pixelRatio, Viewport viewport,
                              MapModel model, ChunkSelection selection, ResetMode mode) {
        double chunksPerPixel = 1 / (viewport.scale() * pixelRatio);
        int[] column = new int[width];
        for (int px = 0; px < width; px++) {
            column[px] = (int) Math.floor(viewport.originX() + (px + 0.5) * chunksPerPixel);
        }
        int previousY = Integer.MIN_VALUE;
        for (int py = 0; py < height; py++) {
            int y = (int) Math.floor(viewport.originY() + (py + 0.5) * chunksPerPixel);
            int rowStart = py * width;
            if (y == previousY) {
                System.arraycopy(argb, rowStart - width, argb, rowStart, width);
                continue;
            }
            previousY = y;
            int previousX = Integer.MIN_VALUE;
            int color = 0;
            for (int px = 0; px < width; px++) {
                int x = column[px];
                if (x != previousX) {
                    color = colorOf(x, y, model, selection, mode);
                    previousX = x;
                }
                argb[rowStart + px] = color;
            }
        }
    }

    /** Colour of one chunk position. */
    public static int colorOf(int x, int y, MapModel model, ChunkSelection selection, ResetMode mode) {
        if (!model.inRegion(x, y)) {
            return Palette.OUTSIDE;
        }
        boolean selected = selection.contains(x, y);
        int level = model.level(x, y);
        if (level == 0) {
            if (selected) {
                return mode == ResetMode.DELETE_SELECTED ? Palette.SELECTED_EMPTY_DELETE : Palette.SELECTED_EMPTY_KEEP;
            }
            boolean odd = ((WorldGeometry.chunkToCell(x) + WorldGeometry.chunkToCell(y)) & 1) != 0;
            return odd ? Palette.EMPTY_B : Palette.EMPTY_A;
        }
        boolean reset = mode == ResetMode.DELETE_SELECTED ? selected : !selected;
        if (reset) {
            return Palette.deleted(level);
        }
        return selected ? Palette.protectedChunk(level) : Palette.kept(level);
    }
}
