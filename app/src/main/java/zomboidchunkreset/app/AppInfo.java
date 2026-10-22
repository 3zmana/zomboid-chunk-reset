package zomboidchunkreset.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Version and source repository of the program, written into {@code app.properties} by the build. */
public final class AppInfo {

    private static final Properties VALUES = load();

    private AppInfo() {
    }

    /** Program version, for example {@code 0.10.0} ({@code dev} when run without the build). */
    public static String version() {
        return value("version", "dev");
    }

    /** Address of the source repository, or an empty string. */
    public static String repositoryUrl() {
        return value("repository", "");
    }

    private static String value(String key, String fallback) {
        String v = VALUES.getProperty(key, "").trim();
        return v.isEmpty() || v.startsWith("${") ? fallback : v;
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppInfo.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            // Missing information is shown as "dev".
        }
        return p;
    }
}
