/*
 * GPL-3.0-or-later. Independent caption time-window planner for Morphe YouTube.
 * The planner only processes local timedtext XML and never accesses the network.
 */
package app.morphe.extension.youtube.patches;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProfanitySchedule {
    private static final int MAX_CAPTIONS = 30000;
    private static final int MAX_SEGMENTS = 100000;
    private static final int MAX_TEXT_CHARS = 500000;
    private static final int MAX_MATCHES = 20000;
    private static final long MAX_PHRASE_GAP_MS = 3000;
    private static final long MERGE_GAP_MS = 150;

    record Window(long start, long end) {}
    record Scan(List<Window> windows, int captionCount, int segmentCount, int matchCount) {}
    private record Piece(int charStart, int charEnd, long timeStart, long timeEnd,
                         boolean approximate) {}

    private ProfanitySchedule() {}

    private static long numeric(Element e, String key, long fallback) {
        String value = e.getAttribute(key);
        if (value.isEmpty()) return fallback;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static void append(StringBuilder text, List<Piece> pieces, String part,
                               long begin, long end, boolean approximate) {
        if (part == null || part.isEmpty()) return;
        int from = text.length();
        text.append(part);
        pieces.add(new Piece(from, text.length(), begin, end, approximate));
    }

    private static List<Element> segments(Element caption) {
        List<Element> result = new ArrayList<>();
        for (Node n = caption.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && "s".equals(e.getTagName())) result.add(e);
        }
        return result;
    }

    private static Piece locate(List<Piece> pieces, int character) {
        int low = 0, high = pieces.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Piece item = pieces.get(mid);
            if (character < item.charStart()) high = mid - 1;
            else if (character >= item.charEnd()) low = mid + 1;
            else return item;
        }
        return null;
    }

    /** Estimate the speech location inside an entire subtitle line if word times are absent. */
    private static long approximateStart(Piece p, int character) {
        if (!p.approximate()) return p.timeStart();
        long span = Math.max(1, p.timeEnd() - p.timeStart());
        double ratio = (double) (character - p.charStart()) /
                Math.max(1, p.charEnd() - p.charStart());
        long estimate = p.timeStart() + Math.round(span * ratio);
        long margin = Math.max(600, Math.round(span * 0.35));
        return Math.max(Math.max(0, p.timeStart() - 500), estimate - margin);
    }

    private static long approximateEnd(Piece p, int character) {
        if (!p.approximate()) return p.timeEnd();
        long span = Math.max(1, p.timeEnd() - p.timeStart());
        double ratio = (double) (character - p.charStart()) /
                Math.max(1, p.charEnd() - p.charStart());
        long estimate = p.timeStart() + Math.round(span * ratio);
        long margin = Math.max(600, Math.round(span * 0.35));
        return Math.min(p.timeEnd() + 500, estimate + margin);
    }

    /** Output is strictly sorted, with overlapping or adjacent windows merged. */
    static Scan scan(Document document, Pattern words, long beforeMs, long fallbackDurationMs) {
        if (document == null || words == null) {
            return new Scan(Collections.emptyList(), 0, 0, 0);
        }
        beforeMs = Math.max(0, Math.min(500, beforeMs));
        fallbackDurationMs = Math.max(100, Math.min(5000, fallbackDurationMs));

        NodeList captions = document.getElementsByTagName("p");
        if (captions.getLength() > MAX_CAPTIONS) {
            return new Scan(Collections.emptyList(), 0, 0, 0);
        }
        StringBuilder fullText = new StringBuilder();
        ArrayList<Piece> pieces = new ArrayList<>();
        int parsedCaptions = 0, parsedSegments = 0;
        long previousEnd = -1;
        for (int k = 0; k < captions.getLength(); k++) {
            if (!(captions.item(k) instanceof Element paragraph)) continue;
            long start = numeric(paragraph, "t", -1), duration = numeric(paragraph, "d", -1);
            if (start < 0 || duration <= 0 || duration > 600000
                    || start > Long.MAX_VALUE - duration) continue;
            long end = start + duration;
            List<Element> children = segments(paragraph);
            if (children.size() + parsedSegments > MAX_SEGMENTS) break;
            if (previousEnd >= 0 && fullText.length() > 0) fullText.append(' ');
            previousEnd = end;
            parsedCaptions++;

            if (children.isEmpty()) {
                append(fullText, pieces, paragraph.getTextContent(),
                        start, end, true);
            } else {
                parsedSegments += children.size();
                for (int n = 0; n < children.size(); n++) {
                    Element segment = children.get(n);
                    String segmentText = segment.getTextContent();
                    long offset = numeric(segment, "t", 0);
                    if (offset < 0 || offset >= duration) continue;
                    long wordStart = start + offset, nextOffset = -1;
                    for (int following = n + 1; following < children.size(); following++) {
                        Element next = children.get(following);
                        // Whitespace-only segments should not end a spoken word.
                        if (next.getTextContent().strip().isEmpty()) continue;
                        nextOffset = numeric(next, "t", -1);
                        break;
                    }
                    boolean nextTimed = nextOffset > offset && nextOffset <= duration;
                    long stop = nextTimed ? start + nextOffset
                            : Math.min(end, wordStart + fallbackDurationMs);
                    stop = Math.max(wordStart + 1, stop);
                    boolean approximate = !nextTimed
                            || segmentText.strip().contains(" ")
                            || segmentText.strip().contains("\n");
                    append(fullText, pieces, segmentText, wordStart, stop, approximate);
                }
            }
            if (fullText.length() > MAX_TEXT_CHARS) {
                return new Scan(Collections.emptyList(), parsedCaptions, parsedSegments, 0);
            }
        }

        ArrayList<Window> found = new ArrayList<>();
        Matcher match = words.matcher(fullText);
        int matchCount = 0;
        while (match.find() && matchCount < MAX_MATCHES) {
            if (match.start() == match.end()) continue;
            Piece first = locate(pieces, match.start());
            Piece last = locate(pieces, match.end() - 1);
            if (first == null || last == null
                    || last.timeStart() < first.timeStart()
                    || last.timeStart() - first.timeEnd() > MAX_PHRASE_GAP_MS) continue;
            long from = Math.max(0, approximateStart(first, match.start()) - beforeMs);
            long to = approximateEnd(last, match.end());
            if (to <= from) to = from + 100;
            if (to - from > 15000) to = from + 15000;
            found.add(new Window(from, to));
            matchCount++;
        }
        if (found.isEmpty()) {
            return new Scan(Collections.emptyList(), parsedCaptions, parsedSegments, matchCount);
        }
        found.sort(Comparator.comparingLong(Window::start));
        ArrayList<Window> merged = new ArrayList<>();
        Window previous = found.get(0);
        for (int j = 1; j < found.size(); j++) {
            Window current = found.get(j);
            if (current.start() <= previous.end() + MERGE_GAP_MS) {
                previous = new Window(previous.start(),
                        Math.max(previous.end(), current.end()));
            } else {
                merged.add(previous);
                previous = current;
            }
        }
        merged.add(previous);
        return new Scan(Collections.unmodifiableList(merged),
                parsedCaptions, parsedSegments, matchCount);
    }
}
