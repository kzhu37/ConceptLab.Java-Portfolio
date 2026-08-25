
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Escaping helpers for the pipe-delimited persistence format used by
 * ConceptLab.
 *
 * <p>
 * The file format stores records as fields separated by the pipe character. Raw
 * user content can include pipes, backslashes, or newlines, so text must be
 * escaped before writing and unescaped after reading.
 */
public final class EscapeUtil {

    private EscapeUtil() {
        // Utility class.
    }

    /**
     * Encodes a value so it can be written safely in a pipe-delimited record.
     *
     * @param value raw text value; null is treated as an empty value
     * @return escaped text that can be stored in a delimited line
     */
    public static String encode(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\|");
    }

    /**
     * Decodes a value that was previously encoded by {@link #encode(String)}.
     *
     * @param encoded escaped value read from storage
     * @return decoded plain text
     */
    public static String decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded must not be null");

        StringBuilder out = new StringBuilder(encoded.length());
        boolean escaping = false;

        for (int i = 0; i < encoded.length(); i++) {
            char ch = encoded.charAt(i);
            if (!escaping) {
                if (ch == '\\') {
                    escaping = true;
                } else {
                    out.append(ch);
                }
                continue;
            }

            // Interpret the escaped token and keep unknown escapes lossless.
            switch (ch) {
                case 'n' ->
                    out.append('\n');
                case '|' ->
                    out.append('|');
                case '\\' ->
                    out.append('\\');
                default -> {
                    out.append('\\');
                    out.append(ch);
                }
            }
            escaping = false;
        }

        if (escaping) {
            throw new IllegalArgumentException("Dangling escape at end of value: " + encoded);
        }

        return out.toString();
    }

    /**
     * Splits an encoded line by delimiter while respecting escaped delimiters.
     *
     * <p>
     * This method intentionally keeps escape characters in each token. Each
     * token is decoded later by {@link #decode(String)}.
     *
     * @param input source text
     * @param delimiter delimiter to split by
     * @return field list with original escaped token content preserved
     */
    public static List<String> splitEscaped(String input, char delimiter) {
        Objects.requireNonNull(input, "input must not be null");

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            // Split only when the delimiter is not escaped.
            if (ch == delimiter && !escaping) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
            if (ch == '\\' && !escaping) {
                escaping = true;
            } else {
                escaping = false;
            }
        }

        parts.add(current.toString());
        return parts;
    }
}
