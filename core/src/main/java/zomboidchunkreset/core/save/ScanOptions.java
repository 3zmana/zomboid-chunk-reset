package zomboidchunkreset.core.save;

/**
 * What a scan reads beyond the file listing.
 *
 * @param readChunkHeaders     read and check the 17-byte header of every chunk file (one small read per file)
 * @param readIsoRegionHeaders read and check the header of every {@code datachunk} file
 */
public record ScanOptions(boolean readChunkHeaders, boolean readIsoRegionHeaders) {

    /** Reads and checks everything the program understands. */
    public static final ScanOptions FULL = new ScanOptions(true, true);

    /** Lists files only. */
    public static final ScanOptions LISTING_ONLY = new ScanOptions(false, false);
}
