package zomboidchunkreset.core.reset;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Replaces a file's content so that it is never left half-written. */
final class AtomicFiles {

    /** Suffix of the temporary file written next to the target. */
    static final String TEMP_SUFFIX = ".zcr-tmp";

    private AtomicFiles() {
    }

    static void write(Path target, byte[] content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        try {
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
