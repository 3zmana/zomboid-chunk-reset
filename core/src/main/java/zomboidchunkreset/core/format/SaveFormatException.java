package zomboidchunkreset.core.format;

import java.io.IOException;
import java.nio.file.Path;

/** Thrown when a save file does not match the expected (verified) format. */
public class SaveFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient Path file;

    public SaveFormatException(Path file, String message) {
        super(file + ": " + message);
        this.file = file;
    }

    /** The file that could not be read. */
    public Path file() {
        return file;
    }
}
