package zomboidchunkreset.core.format;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing helpers for the coordinate-bearing file names used in saves. */
public final class FileNames {

    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,9}");

    private FileNames() {
    }

    /** Parses a whole string as an int coordinate (e.g. a {@code map/} sub-folder name). */
    public static Optional<Integer> parseCoordinate(String text) {
        if (!INTEGER.matcher(text).matches()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(text));
    }

    /**
     * Parses {@code <prefix><x>_<y><suffix>}, for example {@code zpop_42_26.bin}.
     *
     * @return the two coordinates, or empty if the name does not have that exact shape
     */
    public static Optional<int[]> parseXY(String fileName, String prefix, String suffix) {
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)
                || fileName.length() <= prefix.length() + suffix.length()) {
            return Optional.empty();
        }
        String middle = fileName.substring(prefix.length(), fileName.length() - suffix.length());
        Matcher m = XY.matcher(middle);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
    }

    private static final Pattern XY = Pattern.compile("(-?\\d{1,9})_(-?\\d{1,9})");
}
