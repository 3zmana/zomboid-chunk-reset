package zomboidchunkreset.core.save;

import java.nio.file.Path;

/**
 * Something unexpected found while scanning a save. Issues never stop a scan; the user interface
 * decides how to present them (the {@link Type} is stable and can be used as a translation key).
 *
 * @param type   kind of issue
 * @param file   file concerned, may be {@code null}
 * @param detail English technical detail
 */
public record ScanIssue(Type type, Path file, String detail) {

    /** How serious an issue is. */
    public enum Severity {
        INFO, WARNING, ERROR
    }

    /** Kinds of issues. */
    public enum Type {
        /** A file or folder in {@code map/} that does not follow the {@code <x>/<y>.bin} layout. */
        MAP_UNEXPECTED_ENTRY(Severity.INFO),
        /** A chunk file whose header cannot be read. */
        CHUNK_UNREADABLE(Severity.ERROR),
        /** A chunk file whose declared length differs from its size (possibly truncated or being written). */
        CHUNK_LENGTH_MISMATCH(Severity.WARNING),
        /** {@code RegionHeader.bin} cannot be parsed. */
        ISO_HEADER_UNREADABLE(Severity.ERROR),
        /** {@code RegionHeader.bin} lists a chunk whose {@code datachunk} file is missing. */
        ISO_HEADER_ENTRY_WITHOUT_FILE(Severity.WARNING),
        /** A {@code datachunk} file that {@code RegionHeader.bin} does not list. */
        ISO_FILE_NOT_IN_HEADER(Severity.WARNING),
        /** A {@code datachunk} file whose internal header disagrees with its name or size. */
        ISO_FILE_HEADER_MISMATCH(Severity.WARNING),
        /** A {@code datachunk} file that cannot be read. */
        ISO_FILE_UNREADABLE(Severity.ERROR),
        /** {@code players.db} or {@code vehicles.db} cannot be read (possibly locked by a running game). */
        DATABASE_UNREADABLE(Severity.ERROR),
        /** A vehicle whose stored chunk differs from the chunk of its position. */
        VEHICLE_CHUNK_MISMATCH(Severity.WARNING),
        /** A single-player record whose stored chunk differs from the chunk of its position. */
        PLAYER_CHUNK_MISMATCH(Severity.WARNING);

        private final Severity severity;

        Type(Severity severity) {
            this.severity = severity;
        }

        public Severity severity() {
            return severity;
        }
    }

    public Severity severity() {
        return type.severity();
    }

    @Override
    public String toString() {
        return type.severity() + " " + type + (file != null ? " [" + file + "]" : "") + ": " + detail;
    }
}
