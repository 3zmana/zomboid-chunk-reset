package zomboidchunkreset.core.terrain;

/**
 * Coarse kind of a tile, as stored in the tile table. The codes are part of the table file format
 * and must never change; new kinds get new codes.
 */
public enum TileClass {
    /** Not in the table (tiles added by mods, or by a newer game version). */
    UNKNOWN(0),
    /** Known tile that does not matter for the map (furniture, signs, decorations...). */
    OTHER(1),
    WATER(2),
    ROAD(3),
    PAVEMENT(4),
    SAND(5),
    DIRT(6),
    GRASS_LIGHT(7),
    GRASS_MEDIUM(8),
    GRASS_DARK(9),
    FLOOR_INTERIOR(10),
    FLOOR_OTHER(11),
    TREE(12),
    /** Tall grass, bushes and plants. */
    VEGETATION(13),
    /** Walls, fences, doors and windows. */
    WALL(14),
    /** Sloped roof tiles. */
    ROOF(15),
    /** Ceiling tiles (Build 42), drawn as a flat roof from above. */
    CEILING(16);

    private static final TileClass[] BY_CODE = new TileClass[32];

    static {
        for (TileClass c : values()) {
            BY_CODE[c.code] = c;
        }
    }

    private final int code;

    TileClass(int code) {
        this.code = code;
    }

    /** Code stored in the tile table. */
    public int code() {
        return code;
    }

    /** The class with the given table code, {@link #UNKNOWN} for unused codes. */
    public static TileClass ofCode(int code) {
        TileClass c = code >= 0 && code < BY_CODE.length ? BY_CODE[code] : null;
        return c == null ? UNKNOWN : c;
    }

    /** Whether this is a floor (ground) tile. */
    public boolean isFloor() {
        return switch (this) {
            case WATER, ROAD, PAVEMENT, SAND, DIRT, GRASS_LIGHT, GRASS_MEDIUM, GRASS_DARK, FLOOR_INTERIOR,
                 FLOOR_OTHER -> true;
            default -> false;
        };
    }

    /** Whether this is natural ground, where tall grass and bushes are shown. */
    public boolean isNaturalGround() {
        return switch (this) {
            case SAND, DIRT, GRASS_LIGHT, GRASS_MEDIUM, GRASS_DARK -> true;
            default -> false;
        };
    }
}
