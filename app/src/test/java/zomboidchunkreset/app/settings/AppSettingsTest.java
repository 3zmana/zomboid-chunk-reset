package zomboidchunkreset.app.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppSettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaultsThenRoundTrip() {
        Path folder = dir.resolve("config");
        AppSettings s = new AppSettings(folder);
        assertEquals("en", s.language());
        assertEquals(Optional.empty(), s.lastFolder());
        assertEquals(folder.resolve("languages"), s.languagesFolder());

        s.setLanguage("fr");
        s.setLastFolder(dir.resolve("saves"));
        s.save();
        assertTrue(Files.isRegularFile(folder.resolve("settings.properties")));

        AppSettings again = new AppSettings(folder);
        assertEquals("fr", again.language());
        assertEquals(Optional.of(dir.resolve("saves")), again.lastFolder());
    }

    @Test
    void unreadableSettingsGiveDefaults() throws IOException {
        Files.write(dir.resolve("settings.properties"), new byte[] {'a', '=', '\\', 'u', 'Z', 'Z'});
        assertEquals("en", new AppSettings(dir).language());
    }

    @Test
    void defaultFolderIsUnderTheUserProfile() {
        Path folder = AppSettings.defaultFolder();
        assertTrue(folder.getFileName().toString().equalsIgnoreCase("zomboidchunkreset"), folder.toString());
    }
}
