package zomboidchunkreset.core.terrain;

/** What the map shows for one square, seen from above. */
public enum TerrainCategory {
    /** No information (square not stored, or chunk only partly decoded). */
    NONE,
    /** Something not recognised (modded tiles, furniture as the lowest object...). */
    NEUTRAL,
    WATER,
    ROAD,
    PAVEMENT,
    SAND,
    DIRT,
    GRASS_LIGHT,
    GRASS_MEDIUM,
    GRASS_DARK,
    /** Natural ground covered by tall grass, bushes or plants. */
    TALL_GRASS,
    TREE,
    /** Walls, fences, doors and windows (where no roof covers them). */
    WALL,
    /** Building floor (where no roof covers it). */
    INTERIOR,
    /** Sloped roof. */
    ROOF,
    /** Flat roof, ceiling, or an upper floor seen from above. */
    ROOF_FLAT;

    private static final TerrainCategory[] VALUES = values();

    /** The category with the given ordinal. */
    public static TerrainCategory ofOrdinal(int ordinal) {
        return VALUES[ordinal];
    }
}
