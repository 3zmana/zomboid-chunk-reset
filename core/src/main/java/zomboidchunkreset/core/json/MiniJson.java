package zomboidchunkreset.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer, enough for the selection files (no dependency needed).
 *
 * <p>Reading produces {@link Map} (insertion-ordered), {@link List}, {@link String}, {@link Long}
 * (whole numbers), {@link Double}, {@link Boolean} and {@code null}. Writing accepts the same types
 * (and any {@link Number}) and produces indented output.
 */
public final class MiniJson {

    /** Thrown for malformed input. */
    public static final class JsonException extends Exception {
        private static final long serialVersionUID = 1L;

        JsonException(String message, int position) {
            super(message + " (at character " + position + ")");
        }
    }

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parses a complete JSON document. */
    public static Object parse(String text) throws JsonException {
        MiniJson p = new MiniJson(text);
        p.skipWhitespace();
        Object value = p.value(0);
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw new JsonException("unexpected content after the document", p.pos);
        }
        return value;
    }

    private static final int MAX_DEPTH = 64;

    private Object value(int depth) throws JsonException {
        if (depth > MAX_DEPTH) {
            throw new JsonException("nested too deeply", pos);
        }
        if (pos >= text.length()) {
            throw new JsonException("unexpected end", pos);
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw new JsonException("unexpected character '" + c + "'", pos);
        }
    }

    private Map<String, Object> object(int depth) throws JsonException {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("expected a property name", pos);
            }
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, value(depth + 1));
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}'", pos - 1);
            }
        }
    }

    private List<Object> array(int depth) throws JsonException {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(value(depth + 1));
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']'", pos - 1);
            }
        }
    }

    private String string() throws JsonException {
        pos++; // "
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new JsonException("unterminated string", pos);
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new JsonException("bad unicode escape", pos);
                        }
                        try {
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new JsonException("bad unicode escape", pos);
                        }
                        pos += 4;
                    }
                    default -> throw new JsonException("bad escape '\\" + e + "'", pos - 1);
                }
            } else if (c < 0x20) {
                throw new JsonException("control character in string", pos - 1);
            } else {
                sb.append(c);
            }
        }
    }

    private Object number() throws JsonException {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean fraction = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                fraction = true;
                pos++;
            } else {
                break;
            }
        }
        String s = text.substring(start, pos);
        try {
            if (!fraction) {
                return Long.parseLong(s);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new JsonException("bad number '" + s + "'", start);
        }
    }

    private Object literal(String word, Object value) throws JsonException {
        if (!text.startsWith(word, pos)) {
            throw new JsonException("unexpected word", pos);
        }
        pos += word.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() throws JsonException {
        if (pos >= text.length()) {
            throw new JsonException("unexpected end", pos);
        }
        return text.charAt(pos);
    }

    private char next() throws JsonException {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) throws JsonException {
        if (next() != c) {
            throw new JsonException("expected '" + c + "'", pos - 1);
        }
    }

    // ------------------------------------------------------------------ writing

    /** Writes a value as indented JSON. Small objects (only numbers and strings) stay on one line. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            quote(sb, s);
        } else if (value instanceof Boolean || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            sb.append(value);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("not representable in JSON: " + d);
            }
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d));
        } else if (value instanceof Map<?, ?> map) {
            boolean flat = map.values().stream().allMatch(v -> v == null || v instanceof Number || v instanceof String
                    || v instanceof Boolean);
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(first ? "" : ",");
                first = false;
                if (flat) {
                    sb.append(' ');
                } else {
                    newline(sb, indent + 1);
                }
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
            }
            if (!map.isEmpty()) {
                if (flat) {
                    sb.append(' ');
                } else {
                    newline(sb, indent);
                }
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                sb.append(first ? "" : ",");
                first = false;
                newline(sb, indent + 1);
                write(sb, item, indent + 1);
            }
            if (!list.isEmpty()) {
                newline(sb, indent);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + value.getClass());
        }
    }

    private static void newline(StringBuilder sb, int indent) {
        sb.append('\n');
        sb.append("  ".repeat(indent));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
