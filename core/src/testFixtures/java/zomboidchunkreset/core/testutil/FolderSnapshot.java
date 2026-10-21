package zomboidchunkreset.core.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Size and modification time of every file and folder under a root, used to prove that an
 * operation did not change anything on disk.
 */
public final class FolderSnapshot {

    private FolderSnapshot() {
    }

    public static Map<String, String> take(Path root) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                snapshot.put(root.relativize(p).toString(),
                        (a.isDirectory() ? "dir" : a.size() + "B") + "@" + a.lastModifiedTime().toMillis());
            }
        }
        return snapshot;
    }
}
