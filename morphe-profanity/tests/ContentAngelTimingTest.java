package app.morphe.extension.youtube.patches;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/** Synthetic word-timing golden values measured from installed ContentAngel 1.5.1. */
public final class ContentAngelTimingTest {
    public static void main(String[] args) throws Exception {
        int checks = 0;
        var words = ProfanityRules.compile(false, false, "shit,fucking", "", true);
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            String xml = new String(Base64.getDecoder().decode(fields[3]), StandardCharsets.UTF_8);
            var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            var document = builder.parse(new InputSource(new StringReader(xml)));
            var scan = ProfanitySchedule.scan(document, words, Long.parseLong(fields[1]), Long.parseLong(fields[2]));
            String[] windows = fields[4].isEmpty() ? new String[0] : fields[4].split(";");
            if (scan.windows().size() != windows.length) throw new AssertionError(fields[0] + ": window count");
            for (int i = 0; i < windows.length; i++) {
                String[] expected = windows[i].split(":");
                var actual = scan.windows().get(i);
                if (Math.abs(actual.start() - Long.parseLong(expected[0])) > 1
                        || Math.abs(actual.end() - Long.parseLong(expected[1])) > 1)
                    throw new AssertionError(fields[0] + ": " + actual + " != " + windows[i]);
            }
            checks++;
        }
        if (checks == 0) throw new AssertionError("No reference cases were read");
        System.out.println("PASS: " + checks + " ContentAngel 1.5.1 word-timing reference cases");
    }
}
