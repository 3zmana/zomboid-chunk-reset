package zomboidchunkreset.core.reset;

import java.util.List;

/**
 * Result of {@link ResetExecutor#run}. In {@link ResetExecutor.Mode#SIMULATE} mode the counts are
 * what <i>would</i> be done, and nothing was changed.
 *
 * @param mode                  simulation or real run
 * @param outcome               how the run ended
 * @param changesMade           whether anything on disk was changed (always false in simulation)
 * @param message               English explanation when the outcome is not {@link Outcome#COMPLETED}
 * @param chunkFilesDeleted     chunk files deleted (or found already absent)
 * @param chunkBytesDeleted     size of the deleted chunk files
 * @param columnFoldersRemoved  {@code map/<x>/} folders removed because they became empty
 * @param cellFilesDeleted      per-cell files deleted
 * @param isoFilesDeleted       {@code datachunk} files deleted
 * @param regionHeaderRewritten whether {@code RegionHeader.bin} was rewritten
 * @param vehiclesDeleted       {@code vehicles.db} rows deleted
 * @param log                   English summary lines, for display or troubleshooting
 */
public record ResetReport(ResetExecutor.Mode mode, Outcome outcome, boolean changesMade, String message,
                          int chunkFilesDeleted, long chunkBytesDeleted, int columnFoldersRemoved,
                          int cellFilesDeleted, int isoFilesDeleted, boolean regionHeaderRewritten,
                          int vehiclesDeleted, List<String> log) {

    /** How a run ended. */
    public enum Outcome {
        /** Everything planned was done (or, in simulation, could be done). */
        COMPLETED,
        /** A pre-check failed; nothing was changed. */
        ABORTED,
        /** An error stopped the run after some changes; the save was left consistent. */
        STOPPED,
        /** The run was cancelled; what was done so far was left consistent. */
        CANCELLED
    }

    public ResetReport {
        log = List.copyOf(log);
    }
}
