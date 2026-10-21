package zomboidchunkreset.app.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.reset.PlanWarning;
import zomboidchunkreset.core.reset.ResetExecutor;
import zomboidchunkreset.core.reset.ResetReport;
import zomboidchunkreset.core.save.SaveKind;
import zomboidchunkreset.core.save.ScanIssue;

/** Every string the interface asks for must exist, and every translation must be complete. */
class I18nTest {

    private static final Pattern LITERAL_KEY = Pattern.compile("\\bt\\(\"([A-Za-z0-9_.]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @TempDir
    Path dir;

    @AfterEach
    void restore() {
        I18n.use(I18n.DEFAULT, null);
    }

    /** Keys written literally in the source code, as in t("menu.file"). */
    static Set<String> literalKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher m = LITERAL_KEY.matcher(Files.readString(f));
                while (m.find()) {
                    // A key ending with "." is a prefix completed at run time (see dynamicKeys).
                    if (!m.group(1).endsWith(".")) {
                        keys.add(m.group(1));
                    }
                }
            }
        }
        return keys;
    }

    /** Keys built from enum values. */
    static Set<String> dynamicKeys() {
        Set<String> keys = new TreeSet<>();
        for (SaveKind k : SaveKind.values()) {
            keys.add("save.kind." + k);
        }
        for (PlanWarning.Type w : PlanWarning.Type.values()) {
            keys.add("warning." + w);
        }
        for (ScanIssue.Type i : ScanIssue.Type.values()) {
            keys.add("issue." + i);
        }
        for (ResetExecutor.Mode mode : ResetExecutor.Mode.values()) {
            keys.add("report.title." + mode);
            for (ResetReport.Outcome o : ResetReport.Outcome.values()) {
                keys.add("report.outcome." + o + "." + mode);
            }
        }
        return keys;
    }

    @Test
    void everyKeyUsedExistsInEnglish() throws IOException {
        Map<String, String> english = I18n.loadResource("/i18n/messages.properties");
        Set<String> literal = literalKeys();
        assertTrue(literal.size() > 80, "source scan found too few keys: " + literal.size());
        List<String> missing = new ArrayList<>();
        for (String key : literal) {
            if (!english.containsKey(key)) {
                missing.add(key);
            }
        }
        for (String key : dynamicKeys()) {
            if (!english.containsKey(key)) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void everyBuiltInTranslationIsComplete() {
        Map<String, String> english = I18n.loadResource("/i18n/messages.properties");
        List<I18n.Language> languages = I18n.available(null);
        assertEquals("en", languages.get(0).code());
        assertTrue(languages.stream().anyMatch(l -> l.code().equals("fr") && l.name().equals("Français")));
        for (I18n.Language l : languages) {
            if (l.code().equals(I18n.DEFAULT)) {
                continue;
            }
            Map<String, String> other = I18n.loadResource("/i18n/messages_" + l.code() + ".properties");
            assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(other.keySet()), "keys of " + l.code());
            for (String key : english.keySet()) {
                assertFalse(other.get(key).isBlank(), l.code() + ": empty " + key);
                assertEquals(placeholders(english.get(key)), placeholders(other.get(key)),
                        l.code() + ": placeholders of " + key);
            }
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @Test
    void frenchIsUsedWhenChosen() {
        I18n.use("fr", null);
        assertEquals("fr", I18n.code());
        assertEquals("Fichier", I18n.t("menu.file"));
        assertEquals("Supprimer 12 chunks de « pz » ?", I18n.t("confirm.header", "12", "pz"));
        assertEquals("12 345", I18n.number(12345).replace(' ', ' '));
        assertEquals("152,8 Mo", I18n.megabytes(152_800_000).replace(' ', ' '));
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        I18n.use("xx", null);
        assertEquals(I18n.DEFAULT, I18n.code());
        assertEquals("File", I18n.t("menu.file"));
    }

    @Test
    void userLanguageFilesAreFoundAndCompletedWithEnglish() throws IOException {
        Files.writeString(dir.resolve("messages_de.properties"), "language.name=Deutsch\nmenu.file=Datei\n",
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("template_messages.properties"), "menu.file=x\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("notes.txt"), "x", StandardCharsets.UTF_8);
        List<I18n.Language> languages = I18n.available(dir);
        I18n.Language de = languages.stream().filter(l -> l.code().equals("de")).findFirst().orElseThrow();
        assertEquals("Deutsch", de.name());
        assertTrue(de.external());
        assertEquals(3, languages.size(), languages.toString());

        I18n.use("de", dir);
        assertEquals("Datei", I18n.t("menu.file"));
        assertEquals("Edit", I18n.t("menu.edit"), "missing strings fall back to English");
    }

    @Test
    void placeholdersAreReplacedLiterally() {
        assertEquals("1234 MB", I18n.t("unit.megabytes", "1234"));
        assertEquals("!no.such.key!", I18n.t("no.such.key"));
        assertEquals("12,345", I18n.number(12345));
        assertEquals("152.8 MB", I18n.megabytes(152_800_000));
    }
}
