package zomboidchunkreset.core.save;

/**
 * Receives scan progress. Called from the scanning thread. To cancel a scan, interrupt that
 * thread: the scan then stops with an {@link java.io.InterruptedIOException}.
 */
@FunctionalInterface
public interface ScanProgress {

    /** A listener that ignores progress. */
    ScanProgress NONE = (done, total) -> { };

    /**
     * @param done  number of {@code map/} column folders processed
     * @param total total number of {@code map/} column folders
     */
    void update(int done, int total);
}
