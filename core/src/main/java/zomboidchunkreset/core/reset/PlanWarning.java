package zomboidchunkreset.core.reset;

/**
 * Something the user should know before running a reset. The {@link Type} is stable and can be
 * used as a translation key.
 *
 * @param type   kind of warning
 * @param detail English technical detail
 */
public record PlanWarning(Type type, String detail) {

    /** Kinds of warnings. */
    public enum Type {
        /** The plan deletes nothing. */
        NOTHING_TO_DO,
        /** Every chunk of the save would be deleted (for example "keep selected" with an empty selection). */
        ENTIRE_SAVE_RESET,
        /** Players stand in chunks that will be reset. {@code players.db} is not modified. */
        PLAYERS_IN_RESET_AREA,
        /** Global files with world positions are not cleaned (crops, traps, basements...). */
        GLOBAL_FILES_NOT_CLEANED,
        /** {@code RegionHeader.bin} is missing or unreadable: iso-region files are left untouched. */
        ISO_HEADER_UNAVAILABLE,
        /** {@code vehicles.db} could not be read: vehicles are left untouched. */
        VEHICLES_UNAVAILABLE,
        /** The scan reported problems with this save. */
        SCAN_ISSUES_PRESENT
    }
}
