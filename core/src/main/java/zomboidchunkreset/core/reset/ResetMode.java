package zomboidchunkreset.core.reset;

/** How a selection is used by a reset. */
public enum ResetMode {

    /** Reset (delete) the selected chunks; keep everything else. */
    DELETE_SELECTED,

    /** Keep the selected chunks (for example a base); reset everything else. */
    KEEP_SELECTED
}
