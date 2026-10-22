package zomboidchunkreset.core.terrain;

/**
 * Top view of one chunk: a {@link TerrainCategory} for each of its 8 × 8 squares.
 *
 * <p>When the chunk could only be partly decoded, the squares that were not reached are filled with
 * the most common category of the decoded ones ({@link #complete()} is then {@code false}).
 */
public final class ChunkTerrain {

    /** Squares per chunk side. */
    public static final int SIDE = ChunkContentReader.CHUNK_SIDE;

    private final byte[] categories;
    private final boolean complete;
    private final TerrainCategory dominant;

    /**
     * @param categories {@code SIDE * SIDE} ordinals of {@link TerrainCategory}, index {@code x * SIDE + y}
     */
    ChunkTerrain(byte[] categories, boolean complete) {
        if (categories.length != SIDE * SIDE) {
            throw new IllegalArgumentException("expected " + SIDE * SIDE + " squares");
        }
        this.categories = categories.clone();
        this.complete = complete;
        this.dominant = computeDominant(this.categories);
    }

    /** Category of the square at local coordinates (0..7). */
    public TerrainCategory at(int localX, int localY) {
        return TerrainCategory.ofOrdinal(categories[localX * SIDE + localY]);
    }

    /** Ordinal of the category at local coordinates, for fast rendering. */
    public int ordinalAt(int localX, int localY) {
        return categories[localX * SIDE + localY];
    }

    /** Whether every square was decoded. */
    public boolean complete() {
        return complete;
    }

    /** Most common category of the chunk (used when zoomed out). */
    public TerrainCategory dominant() {
        return dominant;
    }

    private static TerrainCategory computeDominant(byte[] categories) {
        int[] counts = new int[TerrainCategory.values().length];
        for (byte c : categories) {
            if (c != TerrainCategory.NONE.ordinal()) {
                counts[c]++;
            }
        }
        int best = TerrainCategory.NONE.ordinal();
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return TerrainCategory.ofOrdinal(best);
    }
}
