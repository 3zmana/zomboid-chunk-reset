package zomboidchunkreset.core.reset;

/**
 * Which associated data is reset together with the chunks. Chunk files are always deleted; the
 * {@code chunkdata} and {@code metacell} files of a fully reset cell are always deleted too
 * (a cell without chunks never has them).
 *
 * @param zombiePopulation delete {@code zpop_X_Y.bin} of fully reset cells
 * @param animalPopulation delete {@code apop_X_Y.bin} of fully reset cells
 * @param isoRegions       delete the {@code datachunk} files of reset chunks and rewrite {@code RegionHeader.bin}
 * @param vehicles         delete the {@code vehicles.db} rows located in reset chunks
 */
public record ResetOptions(boolean zombiePopulation, boolean animalPopulation, boolean isoRegions,
                           boolean vehicles) {

    /** Everything enabled: the default, which gives the cleanest regeneration. */
    public static final ResetOptions ALL = new ResetOptions(true, true, true, true);

    /** Chunk files (and the per-cell technical files) only. */
    public static final ResetOptions CHUNKS_ONLY = new ResetOptions(false, false, false, false);
}
