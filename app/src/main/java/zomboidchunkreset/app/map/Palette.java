package zomboidchunkreset.app.map;

/**
 * Map colours, as opaque ARGB ints. Chunks are coloured by what will happen to them: kept chunks in
 * slate blue, chunks that will be reset in red, protected chunks ("keep selected" mode) in green.
 * The shade gets lighter with the chunk file size.
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

    private Palette() {
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
            double t = (double) i / (colors.length - 1);
            int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
            int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
            int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
            colors[i] = rgb(r, g, b);
        }
        return colors;
    }
}
