/*
 * GPL-3.0-or-later: optional Morphe YouTube profanity filter.
 * Word and phrase matching is deliberately plain Java so it can be unit-tested.
 */
package app.morphe.extension.youtube.patches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProfanityRules {
    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            "fuck", "fucks", "fucked", "fucking", "fucker", "motherfucker", "motherfucking",
            "shit", "shitty", "bullshit", "bitch", "bitches", "bastard", "bastards",
            "asshole", "assholes", "dick", "dicks", "cock", "cocks", "cunt", "cunts",
            "pussy", "pussies", "slut", "sluts", "whore", "whores", "piss", "pissed",
            "damn", "damned", "goddamn", "goddamned", "crap"
    );
    private static final List<String> REVERENT_WORDS = Arrays.asList(
            "oh my god", "oh my gosh", "jesus christ", "god damn", "god dammit",
            "jesus freaking christ", "holy shit"
    );

    private ProfanityRules() {}

    /** Comma, semicolon, or newline separated phrases. Prefix a phrase with '-' to exclude it. */
    static Pattern compile(boolean useDefaults, boolean reverent, String custom) {
        Set<String> terms = new LinkedHashSet<>();
        if (useDefaults) terms.addAll(DEFAULT_WORDS);
        if (reverent) terms.addAll(REVERENT_WORDS);
        List<String> removals = new ArrayList<>();
        if (custom != null) {
            for (String raw : custom.split("[,;\\r\\n]+")) {
                String value = raw.strip().toLowerCase(java.util.Locale.ROOT);
                if (value.isEmpty() || value.length() > 100) continue;
                if (value.startsWith("-")) {
                    if (!value.substring(1).strip().isEmpty()) removals.add(value.substring(1).strip());
                } else {
                    terms.add(value);
                }
            }
        }
        terms.removeAll(removals);
        List<String> parts = new ArrayList<>();
        for (String term : terms) {
            String[] words = term.split("\\s+");
            StringBuilder phrase = new StringBuilder();
            for (String word : words) {
                if (word.isEmpty()) continue;
                if (phrase.length() != 0) phrase.append("\\s+");
                phrase.append(Pattern.quote(word));
            }
            if (phrase.length() != 0) parts.add(phrase.toString());
        }
        if (parts.isEmpty()) return Pattern.compile("(?!)");
        parts.sort((a,b) -> Integer.compare(b.length(), a.length()));
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])(?:" + String.join("|", parts)
                + ")(?![\\p{L}\\p{N}])");
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
            output.append(text, from, matcher.start());
            String match = matcher.group();
            for (int i = 0; i < match.length(); i++) {
                char ch = match.charAt(i);
                output.append(Character.isLetterOrDigit(ch) ? '*' : ch);
            }
            from = matcher.end();
        } while (matcher.find());
        return output.append(text, from, text.length()).toString();
    }
}