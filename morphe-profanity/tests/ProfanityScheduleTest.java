/* Standalone JVM smoke tests: no Android SDK needed. */
package app.morphe.extension.youtube.patches;

import java.io.StringReader;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public final class ProfanityScheduleTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static Document doc(String inner) throws Exception {
        var parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return parser.parse(new InputSource(
                new StringReader("<timedtext format=\"3\"><body>" + inner + "</body></timedtext>")));
    }

    public static void main(String[] args) throws Exception {
        Pattern all = ProfanityRules.compile(true, true, "", "", true);
        check(ProfanityRules.contains("the dog was barking", all) == false,
                "ordinary speech must not be censored");
        check(ProfanityRules.contains("oh my God", all), "faith-friendly expression");
        check(ProfanityRules.contains("what the hell", all), "matching ContentAngel common list");
        check(ProfanityRules.contains("fucking", all), "inflection of common profanity");
        check(ProfanityRules.contains("he said [ __ ] again", all), "YouTube sanitization marker");
        check(!ProfanityRules.contains("hello world", all), "word boundary");
        Pattern excluding = ProfanityRules.compile(true, false, "-hell", "", true);
        check(!ProfanityRules.contains("hell", excluding), "literal exclusions");
        check(ProfanityRules.contains("and [ __ ]", excluding), "marker cannot be excluded");
        Pattern regex = ProfanityRules.compile(false, false, "", "ble+p", true);
        check(ProfanityRules.contains("bleep", regex), "custom regex");
        check(ProfanityRules.contains("[ __ ]", regex), "marker even with no defaults");

        var wordTimed = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"2500\"><s t=\"0\">well </s><s t=\"850\">shit </s>"
                + "<s t=\"1250\">nope</s></p>"), all, 150, 1500);
        check(wordTimed.matchCount() == 1, "one detected profanity word");
        check(wordTimed.windows().size() == 1, "one mute window");
        check(wordTimed.windows().get(0).start() <= 1850, "muting begins before word");
        check(wordTimed.windows().get(0).end() == 2170, "word ends before trailing space and next word");

        var terminal = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"12000\"><s>well </s><s t=\"300\">shit</s></p>"
                + "<p t=\"1900\" d=\"900\">clean word</p>"), all, 150, 1500);
        check(terminal.windows().get(0).start() == 1150, "last word has no extra sentence lead-in");
        check(terminal.windows().get(0).end() == 1900, "rolling line cannot mute the following line");
        var lonely = ProfanitySchedule.scan(doc("<p t=\"1000\" d=\"12000\"><s>shit</s></p>"), all, 150, 1500);
        check(lonely.windows().get(0).end() == 2500, "single word respects fallback without end padding");
        var censoredToken = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"12000\"><s t=\"0\">[ __ ]</s></p>"), all, 150, 500);
        check(censoredToken.windows().get(0).end() == 1500, "placeholder is one word despite its spaces");
        var timedPhrase = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"4000\"><s t=\"0\">well shit </s>"
                + "<s t=\"1000\">clean words</s></p>"), all, 150, 1500);
        check(timedPhrase.windows().get(0).start() == 1350, "timed phrase uses character location");
        check(timedPhrase.windows().get(0).end() == 1900, "timed phrase has no sentence-wide margin");
        var formatted = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"3000\"><s>well </s><s>shit</s></p>"), all, 150, 1500);
        check(formatted.windows().get(0).start() > 1000, "format-only segments are not fake zero-offset words");
        var blankLine = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"9000\"><s t=\"0\">shit</s></p>"
                + "<p t=\"1200\" d=\"1000\"> </p><p t=\"2000\" d=\"1000\">clean</p>"), all, 150, 1500);
        check(blankLine.windows().get(0).end() == 2000, "empty rendering events are not word-end anchors");
        var partialPattern = ProfanityRules.compile(false, false, "fuck", "", false);
        var partial = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"3000\"><s t=\"0\">fucking</s><s t=\"700\">clean</s></p>"),
                partialPattern, 0, 1500);
        check(partial.windows().get(0).end() == 1400, "partial match uses only matched character times");

        var betweenLines = ProfanitySchedule.scan(doc(
                "<p t=\"1000\" d=\"600\">oh my</p>"
                + "<p t=\"1800\" d=\"600\">God</p>"), all, 150, 1500);
        check(betweenLines.matchCount() > 0, "phrase spanning caption paragraphs");
        check(betweenLines.windows().get(0).end() > 1800,
                "phrase window extends into second paragraph");

        var marker = ProfanitySchedule.scan(doc(
                "<p t=\"3000\" d=\"900\"><s t=\"0\">something </s>"
                + "<s t=\"400\">[ __ ]</s></p>"), all, 150, 1500);
        check(marker.matchCount() == 1, "caption's censored-word marker");
        var empty = ProfanitySchedule.scan(doc("<p t=\"5000\" d=\"1500\">ordinary speech</p>"),
                all, 150, 1500);
        check(empty.windows().isEmpty(), "no false positive silence");

        System.out.println("PASS: " + checks + " profanity-rule and timing checks");
    }
}
