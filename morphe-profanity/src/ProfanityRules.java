/*
 * GPL-3.0-or-later. Word and phrase detection for the optional Morphe filter.
 * This is an independent Java implementation of caption-based filtering.
 */
package app.morphe.extension.youtube.patches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class ProfanityRules {
    // YouTube's own sanitized transcript marker must be caught even if every
    // editable word list is empty. It is not actual spoken text.
    private static final String ALWAYS_MUTED = "\\[[\\s\\p{Z}]*_{2,}[\\s\\p{Z}]*\\]";
    private static final String BOUNDARY_LEFT = "(?<![\\p{L}\\p{N}])";
    private static final String BOUNDARY_RIGHT = "(?![\\p{L}\\p{N}])";

    private ProfanityRules() {}

    private static Map<String, String> defaultExpressions(boolean language, boolean reverent) {
        Map<String, String> words = new LinkedHashMap<>();
        if (language) {
            words.put("fuck", "fuck\\p{L}*");
            words.put("motherfucker", "motherfuck\\p{L}*");
            words.put("shit", "shit\\p{L}*");
            words.put("bitch", "bitch\\p{L}*");
            words.put("whore", "whor\\p{L}*");
            words.put("piss", "piss\\p{L}*");
            words.put("damn", "\\p{L}*damn\\p{L}*");
            for (String term : Arrays.asList(
                    "hell", "crap", "ass", "asshole", "bastard", "dick",
                    "cock", "pussy", "slut", "cunt", "bullshit")) {
                words.put(term, Pattern.quote(term));
            }
        }
        if (reverent) {
            words.put("jesus", Pattern.quote("Jesus"));
            words.put("christ", Pattern.quote("Christ"));
            words.put("god", Pattern.quote("God"));
            words.put("jesus christ", "Jesus\\s+(?:\\p{L}+\\s+)?Christ");
            words.put("oh my god", "oh\\s+my\\s+(?:\\p{L}+\\s+)?God");
        }
        return words;
    }

    private static String literalPattern(String term) {
        StringBuilder result = new StringBuilder();
        for (String part : term.trim().split("\\s+")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append("\\s+");
            result.append(Pattern.quote(part));
        }
        return result.toString();
    }

    // Kept for older test fixtures and callers.
    static Pattern compile(boolean defaults, boolean reverent, String custom) {
        return compile(defaults, reverent, custom, "", true);
    }

    /** Custom words are literal; customRegex is an advanced opt-in regex list. */
    static Pattern compile(boolean defaults, boolean reverent, String custom,
                           String customRegex, boolean boundaries) {
        Map<String, String> terms = defaultExpressions(defaults, reverent);
        Set<String> exclusions = new LinkedHashSet<>();
        if (custom != null) {
            for (String raw : custom.split("[,;\\r\\n]+")) {
                String value = raw.strip().toLowerCase(Locale.ROOT);
                if (value.isEmpty() || value.length() > 100) continue;
                if (value.startsWith("-")) {
                    String removed = value.substring(1).strip();
                    if (!removed.isEmpty()) exclusions.add(removed);
                } else {
                    terms.put(value, literalPattern(value));
                }
            }
        }
        for (String term : exclusions) terms.remove(term);

        List<String> expressions = new ArrayList<>();
        expressions.add(ALWAYS_MUTED);
        for (String expression : terms.values()) {
            if (expression.isBlank()) continue;
            String pattern = boundaries
                    ? BOUNDARY_LEFT + "(?:" + expression + ")" + BOUNDARY_RIGHT
                    : "(?:" + expression + ")";
            expressions.add(pattern);
        }

        if (customRegex != null && !customRegex.isBlank()) {
            int count = 0;
            for (String raw : customRegex.split("[;\\r\\n]+")) {
                if (++count > 50) break;
                String term = raw.trim();
                if (term.length() < 1 || term.length() > 200) continue;
                try {
                    Pattern.compile(term, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    expressions.add("(?:" + term + ")");
                } catch (PatternSyntaxException ignored) {
                    // Ignore only the invalid entry instead of disabling everything.
                }
            }
        }
        // Longest candidates first for better overlap handling.
        expressions.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return Pattern.compile("(?iu)(?:" + String.join("|", expressions) + ")");
    }

    static boolean contains(String text, Pattern pattern) {
        return text != null && pattern.matcher(text).find();
    }

    static String censor(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) return text;
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        StringBuilder output = new StringBuilder(text.length());
        int from = 0;
        do {
            if (matcher.start() == matcher.end()) continue;
            output.append(text, from, matcher.start());
            for (char ch : matcher.group().toCharArray()) {
                output.append(Character.isLetterOrDigit(ch) || ch == '_'
                        ? '*' : ch);
            }
            from = matcher.end();
        } while (matcher.find());
        output.append(text, from, text.length());
        return output.toString();
    }
}
