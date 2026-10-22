package zomboidchunkreset.app.map;

import zomboidchunkreset.core.terrain.TerrainCategory;

/**
 * Map colours, as opaque ARGB ints. Chunks are coloured by what will happen to them: kept chunks in
 * slate blue, chunks that will be reset in red, protected chunks ("keep selected" mode) in green.
 * The shade gets lighter with the chunk file size.
 *
 * <p>When the top view of a chunk is known, its squares are drawn in terrain colours instead: natural
 * colours for kept chunks, tinted red for chunks that will be reset and green for protected chunks.
 */
public final class Palette {

    public static final int OUTSIDE = rgb(0x17, 0x19, 0x1c);
    public static final int EMPTY_A = rgb(0x23, 0x26, 0x2b);
    public static final int EMPTY_B = rgb(0x27, 0x2a, 0x30);
    public static final int SELECTED_EMPTY_DELETE = rgb(0x4d, 0x2b, 0x2e);
    public static final int SELECTED_EMPTY_KEEP = rgb(0x26, 0x47, 0x34);

    private static final int[] KEPT = ramp(rgb(0x3b, 0x4a, 0x5c), rgb(0x8f, 0xa8, 0xc2));
    private static final int[] DELETED = ramp(rgb(0x7d, 0x2f, 0x33), rgb(0xe0, 0x6c, 0x6c));
    private static final int[] PROTECTED = ramp(rgb(0x2c, 0x6b, 0x45), rgb(0x7c, 0xd0, 0x92));

    // Overlay colours (CSS strings, used by the JavaFX canvas).
    public static final String CELL_LINE = "rgba(255,255,255,0.18)";
    public static final String CHUNK_LINE = "rgba(255,255,255,0.07)";
    public static final String DRAG_ADD = "#f2c14e";
    public static final String DRAG_REMOVE = "#9aa5b1";
    public static final String HOVER = "rgba(255,255,255,0.85)";
    public static final String PLAYER = "#ffd166";
    public static final String PLAYER_OUTLINE = "#1b1d21";

    /** Chunk states, for terrain tints. */
    public static final int STATE_KEPT = 0;
    public static final int STATE_RESET = 1;
    public static final int STATE_PROTECTED = 2;

    private static final int RESET_TINT = rgb(0xe8, 0x3a, 0x3a);
    private static final int PROTECTED_TINT = rgb(0x2e, 0xd0, 0x6e);
    private static final int[][] TERRAIN = new int[3][];

    static {
        int[] natural = new int[TerrainCategory.values().length];
        natural[TerrainCategory.NONE.ordinal()] = rgb(0x34, 0x37, 0x3c);
        natural[TerrainCategory.NEUTRAL.ordinal()] = rgb(0x96, 0x96, 0x8c);
        natural[TerrainCategory.WATER.ordinal()] = rgb(0x34, 0x62, 0xa0);
        natural[TerrainCategory.ROAD.ordinal()] = rgb(0x4e, 0x4e, 0x52);
        natural[TerrainCategory.PAVEMENT.ordinal()] = rgb(0x80, 0x7e, 0x78);
        natural[TerrainCategory.SAND.ordinal()] = rgb(0xd6, 0xc4, 0x8c);
        natural[TerrainCategory.DIRT.ordinal()] = rgb(0x84, 0x68, 0x48);
        natural[TerrainCategory.GRASS_LIGHT.ordinal()] = rgb(0x7c, 0xa0, 0x4e);
        natural[TerrainCategory.GRASS_MEDIUM.ordinal()] = rgb(0x66, 0x8e, 0x40);
        natural[TerrainCategory.GRASS_DARK.ordinal()] = rgb(0x50, 0x78, 0x34);
        natural[TerrainCategory.TALL_GRASS.ordinal()] = rgb(0x4a, 0x70, 0x30);
        natural[TerrainCategory.TREE.ordinal()] = rgb(0x24, 0x4e, 0x24);
        natural[TerrainCategory.WALL.ordinal()] = rgb(0x5c, 0x48, 0x3c);
        natural[TerrainCategory.INTERIOR.ordinal()] = rgb(0xb0, 0x98, 0x7c);
        natural[TerrainCategory.ROOF.ordinal()] = rgb(0x96, 0x52, 0x42);
        natural[TerrainCategory.ROOF_FLAT.ordinal()] = rgb(0x76, 0x76, 0x7c);
        TERRAIN[STATE_KEPT] = natural;
        TERRAIN[STATE_RESET] = tinted(natural, RESET_TINT, 0.5);
        TERRAIN[STATE_PROTECTED] = tinted(natural, PROTECTED_TINT, 0.4);
    }

    private Palette() {
    }

    /** Terrain colours (indexed by {@link TerrainCategory} ordinal) for a chunk state. */
    public static int[] terrain(int state) {
        return TERRAIN[state];
    }

    /** Natural colour of a terrain category, for legends. */
    public static int terrainColor(TerrainCategory category) {
        return TERRAIN[STATE_KEPT][category.ordinal()];
    }

    private static int[] tinted(int[] colors, int tint, double amount) {
        int[] result = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            result[i] = mix(colors[i], tint, amount);
        }
        return result;
    }

    static int mix(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return rgb(r, g, b);
    }

    /** Kept chunk of the given size level (1..{@link MapModel#LEVELS}). */
    public static int kept(int level) {
        return KEPT[level - 1];
    }

    /** Chunk that will be reset. */
    public static int deleted(int level) {
        return DELETED[level - 1];
    }

    /** Selected chunk in "keep selected" mode. */
    public static int protectedChunk(int level) {
        return PROTECTED[level - 1];
    }

    /** CSS colour of an ARGB int, for legends. */
    public static String css(int argb) {
        return String.format("#%06x", argb & 0xFF_FFFF);
    }

    static int rgb(int r, int g, int b) {
        return 0xFF00_0000 | (r << 16) | (g << 8) | b;
    }

    private static int[] ramp(int from, int to) {
        int[] colors = new int[MapModel.LEVELS];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = mix(from, to, (double) i / (colors.length - 1));
        }
        return colors;
    }
}
