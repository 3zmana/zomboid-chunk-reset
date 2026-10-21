package zomboidchunkreset.app.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-interface strings, read from language files ({@code messages.properties} for English, the
 * default, and {@code messages_<code>.properties} for other languages, all in UTF-8).
 *
 * <p>Built-in languages are listed in {@code i18n/languages.properties}. Users can add a language
 * without rebuilding the program: put {@code messages_<code>.properties} in the {@code languages}
 * folder of the settings folder. A missing string falls back to English.
 *
 * <p>Placeholders are written {@code {0}}, {@code {1}}... and replaced literally: unlike
 * {@link java.text.MessageFormat}, apostrophes need no escaping. Arguments are inserted as-is:
 * format counts with {@link #number(long)} (coordinates are left without digit grouping).
 */
public final class I18n {

    /** Code of the default language. */
    public static final String DEFAULT = "en";

    /** Key holding a language's own name, e.g. "Français". */
    public static final String NAME_KEY = "language.name";

    private static final String BASE = "/i18n/messages";
    private static final Pattern EXTERNAL_FILE = Pattern.compile("messages_([A-Za-z]{2,3}(?:_[A-Za-z0-9]{2,8})?)\\.properties");

    /** An available language. */
    public record Language(String code, String name, Path file) {
        /** Whether it comes from a user file rather than the program. */
        public boolean external() {
            return file != null;
        }
    }

    private static final Map<String, String> ENGLISH = loadResource(BASE + ".properties");
    private static Map<String, String> current = ENGLISH;
    private static String code = DEFAULT;
    private static Locale locale = Locale.ENGLISH;

    private I18n() {
    }

    // ------------------------------------------------------------------ languages

    /** Built-in languages, then user files from {@code externalFolder} (may be null or missing). */
    public static List<Language> available(Path externalFolder) {
        Map<String, Language> result = new LinkedHashMap<>();
        Map<String, String> builtIn = loadResource("/i18n/languages.properties");
        List<String> codes = new ArrayList<>(builtIn.keySet());
        // English first, then by name.
        codes.sort((a, b) -> a.equals(DEFAULT) ? -1 : b.equals(DEFAULT) ? 1
                : builtIn.get(a).compareToIgnoreCase(builtIn.get(b)));
        for (String c : codes) {
            result.put(c, new Language(c, builtIn.get(c), null));
        }
        if (externalFolder != null && Files.isDirectory(externalFolder)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(externalFolder)) {
                for (Path f : files) {
                    Matcher m = EXTERNAL_FILE.matcher(f.getFileName().toString());
                    if (m.matches()) {
                        String c = m.group(1);
                        Map<String, String> strings = loadFile(f);
                        result.put(c, new Language(c, strings.getOrDefault(NAME_KEY, c), f));
                    }
                }
            } catch (IOException e) {
                // ignore an unreadable folder
            }
        }
        return new ArrayList<>(result.values());
    }

    /** Switches language; unknown codes fall back to English. */
    public static void use(String languageCode, Path externalFolder) {
        Map<String, String> strings = null;
        for (Language l : available(externalFolder)) {
            if (l.code().equals(languageCode)) {
                strings = l.external() ? loadFile(l.file())
                        : l.code().equals(DEFAULT) ? ENGLISH : loadResource(BASE + "_" + l.code() + ".properties");
            }
        }
        if (strings == null || strings.isEmpty()) {
            current = ENGLISH;
            code = DEFAULT;
        } else {
            current = strings;
            code = languageCode;
        }
        locale = Locale.forLanguageTag(code.replace('_', '-'));
    }

    /** Code of the language in use. */
    public static String code() {
        return code;
    }

    public static Locale locale() {
        return locale;
    }

    // ------------------------------------------------------------------ strings

    /** The string for {@code key} with {@code {n}} placeholders replaced by {@code args}. */
    public static String t(String key, Object... args) {
        String text = current.get(key);
        if (text == null || text.isEmpty()) {
            text = ENGLISH.get(key);
        }
        if (text == null) {
            return "!" + key + "!";
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /** Whether the language in use defines this key itself (used by tests). */
    public static boolean has(String key) {
        return current.containsKey(key);
    }

    /** A whole number with the language's digit grouping. */
    public static String number(long value) {
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    /** A size in megabytes with one decimal, e.g. "152.8 MB". */
    public static String megabytes(long bytes) {
        NumberFormat f = NumberFormat.getNumberInstance(locale);
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(1);
        return t("unit.megabytes", f.format(bytes / 1_000_000.0));
    }

    /** A size in kilobytes with one decimal. */
    public static String kilobytes(long bytes) {
        NumberFormat f = NumberFormat.getNumberInstance(locale);
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(1);
        return f.format(bytes / 1024.0);
    }

    // ------------------------------------------------------------------ loading

    /** Reads a language file of the program (UTF-8). */
    public static Map<String, String> loadResource(String resource) {
        try (InputStream in = I18n.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Map.of();
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Map.of();
        }
    }

    /** Reads a user language file (UTF-8). */
    public static Map<String, String> loadFile(Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(r);
        } catch (IOException | IllegalArgumentException e) {
            return Map.of();
        }
    }

    private static Map<String, String> read(Reader reader) throws IOException {
        Properties p = new Properties();
        p.load(reader);
        Map<String, String> map = new HashMap<>();
        for (String k : p.stringPropertyNames()) {
            map.put(k, p.getProperty(k));
        }
        return map;
    }
}
