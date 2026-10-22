package zomboidchunkreset.app.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * User settings, stored as {@code settings.properties} in the user's configuration folder:
 * {@code %APPDATA%\ZomboidChunkReset} on Windows, {@code ~/Library/Application Support/ZomboidChunkReset}
 * on macOS, {@code $XDG_CONFIG_HOME/zomboidchunkreset} (default {@code ~/.config/zomboidchunkreset})
 * elsewhere. The same folder holds user language files in {@code languages/}.
 */
public final class AppSettings {

    private static final String FILE = "settings.properties";
    private static final String LANGUAGE = "language";
    private static final String LAST_FOLDER = "lastFolder";
    private static final String SHOW_TERRAIN = "showTerrain";

    private final Path folder;
    private final Properties values = new Properties();

    /** Settings stored in the given folder (created when saving). */
    public AppSettings(Path folder) {
        this.folder = folder;
        Path file = folder.resolve(FILE);
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                values.load(r);
            } catch (IOException | IllegalArgumentException e) {
                // unreadable settings: start from defaults
                values.clear();
            }
        }
    }

    /** Settings in the platform's usual configuration folder. */
    public static AppSettings load() {
        return new AppSettings(defaultFolder());
    }

    /** The platform's configuration folder for this program. */
    public static Path defaultFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "ZomboidChunkReset");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "ZomboidChunkReset");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return Path.of(xdg != null && !xdg.isBlank() ? xdg : Path.of(home, ".config").toString(), "zomboidchunkreset");
    }

    /** Folder of this program's settings. */
    public Path folder() {
        return folder;
    }

    /** Folder where users can add language files ({@code messages_xx.properties}). */
    public Path languagesFolder() {
        return folder.resolve("languages");
    }

    /** Chosen language code; English when never chosen. */
    public String language() {
        return values.getProperty(LANGUAGE, "en");
    }

    public void setLanguage(String code) {
        values.setProperty(LANGUAGE, code);
    }

    /** Folder of the last opened save's parent, if remembered. */
    public Optional<Path> lastFolder() {
        String v = values.getProperty(LAST_FOLDER);
        try {
            return v == null ? Optional.empty() : Optional.of(Path.of(v));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public void setLastFolder(Path path) {
        if (path != null) {
            values.setProperty(LAST_FOLDER, path.toString());
        }
    }

    /** Whether the map draws terrain and roofs (on by default). */
    public boolean showTerrain() {
        return !"false".equals(values.getProperty(SHOW_TERRAIN));
    }

    public void setShowTerrain(boolean show) {
        values.setProperty(SHOW_TERRAIN, Boolean.toString(show));
    }

    /** Writes the settings; failures are ignored (settings are a convenience). */
    public void save() {
        try {
            Files.createDirectories(folder);
            try (Writer w = Files.newBufferedWriter(folder.resolve(FILE), StandardCharsets.UTF_8)) {
                values.store(w, "ZomboidChunkReset settings");
            }
        } catch (IOException e) {
            // read-only profile, etc.
        }
    }
}
