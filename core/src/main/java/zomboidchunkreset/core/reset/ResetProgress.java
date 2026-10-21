package zomboidchunkreset.core.reset;

/**
 * Receives reset progress, from the thread running the reset. To cancel, interrupt that thread:
 * file deletion stops, then the save is left consistent (see {@link ResetExecutor}).
 */
@FunctionalInterface
public interface ResetProgress {

    /** A listener that ignores progress. */
    ResetProgress NONE = (done, total) -> { };

    void update(int done, int total);
}
