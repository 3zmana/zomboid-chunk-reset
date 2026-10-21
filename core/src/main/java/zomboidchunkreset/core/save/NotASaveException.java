package zomboidchunkreset.core.save;

import java.io.IOException;
import java.nio.file.Path;

/** Thrown when a folder does not look like a Project Zomboid save. */
public class NotASaveException extends IOException {

    private static final long serialVersionUID = 1L;

    public NotASaveException(Path folder, String reason) {
        super(folder + " is not a Project Zomboid save folder: " + reason);
    }
}
