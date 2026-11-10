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
        check(wordTimed.windows().get(0).end() >= 2250, "muting continues through word");

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
