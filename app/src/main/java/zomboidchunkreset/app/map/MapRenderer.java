package zomboidchunkreset.app.map;

import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.selection.ChunkSelection;
import zomboidchunkreset.core.terrain.ChunkTerrain;

/**
 * Paints the map into an ARGB pixel buffer. No JavaFX dependency.
 *
 * <p>Each chunk is drawn either with its plain state colour ({@link #colorOf}) or, when its top view
 * is known and terrain display is on, square by square in terrain colours tinted by its state. When
 * a pixel covers more than two squares, the chunk's most common terrain colour is used instead of
 * single squares (no flicker when zoomed out). Chunk state is looked up once per chunk change along a
 * row, and identical rows are copied, so the cost is bounded by the number of pixels.
 */
public final class MapRenderer {

    private static final int SQUARES = WorldGeometry.CHUNK_SIZE_IN_SQUARES;

    /** Physical pixels per chunk from which single squares are drawn. */
    static final double SQUARE_DETAIL_PIXELS_PER_CHUNK = SQUARES / 2.0;

    private MapRenderer() {
    }

    /**
     * @param argb        destination, {@code width * height} pixels, row by row
     * @param width       buffer width in physical pixels
     * @param height      buffer height in physical pixels
     * @param pixelRatio  physical pixels per logical pixel (screen scaling, e.g. 1.5 on a 150% display)
     * @param showTerrain draw the chunks' top views where they are known
     */
    public static void render(int[] argb, int width, int height, double pixelRatio, Viewport viewport,
                              MapModel model, ChunkSelection selection, ResetMode mode, boolean showTerrain) {
        double chunksPerPixel = 1 / (viewport.scale() * pixelRatio);
        boolean squareDetail = viewport.scale() * pixelRatio >= SQUARE_DETAIL_PIXELS_PER_CHUNK;
        TerrainLayer terrain = showTerrain ? model.terrain() : null;

        // Column -> square X (or chunk X * SQUARES when squares are not drawn).
        int[] columnSquare = new int[width];
        for (int px = 0; px < width; px++) {
            columnSquare[px] = (int) Math.floor((viewport.originX() + (px + 0.5) * chunksPerPixel) * SQUARES);
        }
        int previousSquareY = Integer.MIN_VALUE;
        for (int py = 0; py < height; py++) {
            int squareY = (int) Math.floor((viewport.originY() + (py + 0.5) * chunksPerPixel) * SQUARES);
            int chunkY = Math.floorDiv(squareY, SQUARES);
            int rowStart = py * width;
            // Same square row (or same chunk row without square detail): copy the previous row.
            if (py > 0 && (squareY == previousSquareY
                    || (!squareDetail && chunkY == Math.floorDiv(previousSquareY, SQUARES)))) {
                System.arraycopy(argb, rowStart - width, argb, rowStart, width);
                continue;
            }
            previousSquareY = squareY;
            int localY = Math.floorMod(squareY, SQUARES);

            int previousChunkX = Integer.MIN_VALUE;
            int plain = 0;
            ChunkTerrain chunkTerrain = null;
            int[] colors = null;
            for (int px = 0; px < width; px++) {
                int squareX = columnSquare[px];
                int chunkX = Math.floorDiv(squareX, SQUARES);
                if (chunkX != previousChunkX) {
                    previousChunkX = chunkX;
                    plain = colorOf(chunkX, chunkY, model, selection, mode);
                    chunkTerrain = terrain != null && model.hasChunk(chunkX, chunkY) ? terrain.get(chunkX, chunkY) : null;
                    if (chunkTerrain != null) {
                        colors = Palette.terrain(stateOf(chunkX, chunkY, selection, mode));
                        if (!squareDetail) {
                            plain = colors[chunkTerrain.dominant().ordinal()];
                        }
                    }
                }
                if (chunkTerrain != null && squareDetail) {
                    argb[rowStart + px] = colors[chunkTerrain.ordinalAt(Math.floorMod(squareX, SQUARES), localY)];
                } else {
                    argb[rowStart + px] = plain;
                }
            }
        }
    }

    /** Tint state of an existing chunk. */
    static int stateOf(int x, int y, ChunkSelection selection, ResetMode mode) {
        boolean selected = selection.contains(x, y);
        boolean reset = mode == ResetMode.DELETE_SELECTED ? selected : !selected;
        if (reset) {
            return Palette.STATE_RESET;
        }
        return selected ? Palette.STATE_PROTECTED : Palette.STATE_KEPT;
    }

    /** Plain colour of one chunk position. */
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
        return switch (stateOf(x, y, selection, mode)) {
            case Palette.STATE_RESET -> Palette.deleted(level);
            case Palette.STATE_PROTECTED -> Palette.protectedChunk(level);
            default -> Palette.kept(level);
        };
    }
}
