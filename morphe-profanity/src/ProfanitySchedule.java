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
    private static final Pattern CAPTION_WORD = Pattern.compile("\\[\\s*_+\\s*\\]|\\S+", Pattern.UNICODE_CHARACTER_CLASS);

    record Window(long start, long end) {}
    record Scan(List<Window> windows, int captionCount, int segmentCount, int matchCount) {}
    private record Piece(int charStart, int charEnd, long timeStart, long timeEnd,
                         boolean approximate, boolean anchoredWord) {}

    private ProfanitySchedule() {}

    private static long numeric(Element e, String key, long fallback) {
        String value = e.getAttribute(key);
        if (value.isEmpty()) return fallback;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static void append(StringBuilder text, List<Piece> pieces, String part,
                               long begin, long end, boolean approximate, boolean anchored) {
        if (part == null || part.isEmpty()) return;
        int from = text.length();
        text.append(part);
        pieces.add(new Piece(from, text.length(), begin, end, approximate, anchored && singleWord(part)));
    }

    private static boolean singleWord(String text) {
        Matcher tokens = CAPTION_WORD.matcher(text);
        return tokens.find() && !tokens.find();
    }

    private static long nextLineAfter(long[] starts, long time, long fallback) {
        int low = 0, high = starts.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (starts[mid] <= time) low = mid + 1;
            else high = mid;
        }
        return low < starts.length ? Math.min(fallback, starts[low]) : fallback;
    }

    private static long characterTime(Piece p, int character) {
        double fraction = (double)(character - p.charStart()) / Math.max(1, p.charEnd() - p.charStart());
        return p.timeStart() + Math.round((p.timeEnd() - p.timeStart()) * fraction);
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
    private static long approximateStart(Piece p, int character, boolean shortEstimates) {
        if (!p.approximate() || (shortEstimates && !p.anchoredWord())) return characterTime(p, character);
        if (p.anchoredWord()) return p.timeStart();
        long span = Math.max(1, p.timeEnd() - p.timeStart());
        double ratio = (double) (character - p.charStart()) /
                Math.max(1, p.charEnd() - p.charStart());
        long estimate = p.timeStart() + Math.round(span * ratio);
        long margin = Math.max(600, Math.round(span * 0.35));
        return Math.max(Math.max(0, p.timeStart() - 500), estimate - margin);
    }

    private static long approximateEnd(Piece p, int character, boolean shortEstimates) {
        if (!p.approximate() || (shortEstimates && !p.anchoredWord())) return characterTime(p, character);
        if (p.anchoredWord()) return p.timeEnd();
        long span = Math.max(1, p.timeEnd() - p.timeStart());
        double ratio = (double) (character - p.charStart()) /
                Math.max(1, p.charEnd() - p.charStart());
        long estimate = p.timeStart() + Math.round(span * ratio);
        long margin = Math.max(600, Math.round(span * 0.35));
        return Math.min(p.timeEnd(), estimate + margin);
    }

    /** Output is strictly sorted, with overlapping or adjacent windows merged. */
    static Scan scan(Document document, Pattern words, long beforeMs, long fallbackDurationMs) {
        return scan(document, words, beforeMs, fallbackDurationMs, false);
    }

    /** Short estimates interpolate within a sentence without broad safety margins.
     * They are not measured speech boundaries and may miss misaligned speech. */
    static Scan scan(Document document, Pattern words, long beforeMs, long fallbackDurationMs,
                     boolean shortEstimates) {
        if (document == null || words == null) {
            return new Scan(Collections.emptyList(), 0, 0, 0);
        }
        beforeMs = Math.max(0, Math.min(500, beforeMs));
        fallbackDurationMs = Math.max(100, Math.min(5000, fallbackDurationMs));

        NodeList captions = document.getElementsByTagName("p");
        if (captions.getLength() > MAX_CAPTIONS) {
            return new Scan(Collections.emptyList(), 0, 0, 0);
        }
        // Rolling captions remain visible after the next line starts. Their
        // display duration must not be treated as the last word's speech duration.
        ArrayList<Long> starts = new ArrayList<>();
        for (int k = 0; k < captions.getLength(); k++) {
            if (!(captions.item(k) instanceof Element p) || p.getTextContent().isBlank()) continue;
            long t = numeric(p, "t", -1), d = numeric(p, "d", -1);
            if (t >= 0 && d > 0 && d <= 600000 && t <= Long.MAX_VALUE - d) starts.add(t);
        }
        long[] lineStarts = starts.stream().mapToLong(Long::longValue).sorted().distinct().toArray();
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
            parsedSegments += children.size();

            boolean timedChildren = children.stream().anyMatch(e -> e.hasAttribute("t"));
            if (children.isEmpty() || !timedChildren) {
                String text = paragraph.getTextContent();
                long limit = singleWord(text) ? start + Math.min(duration, fallbackDurationMs) : end;
                append(fullText, pieces, text,
                        start, nextLineAfter(lineStarts, start, limit), true, true);
            } else {
                long lastOffset = 0;
                boolean firstWord = true;
                for (int n = 0; n < children.size(); n++) {
                    Element segment = children.get(n);
                    String segmentText = segment.getTextContent();
                    long offset = numeric(segment, "t", lastOffset);
                    if (offset < 0 || offset >= duration) continue;
                    lastOffset = offset;
                    boolean anchored = segment.hasAttribute("t") || firstWord;
                    long wordStart = start + offset, nextOffset = -1;
                    for (int following = n + 1; following < children.size(); following++) {
                        Element next = children.get(following);
                        // Whitespace-only segments should not end a spoken word.
                        if (next.getTextContent().strip().isEmpty()) continue;
                        nextOffset = numeric(next, "t", -1);
                        break;
                    }
                    boolean nextTimed = anchored && nextOffset > offset && nextOffset <= duration;
                    long stop = nextTimed ? start + nextOffset
                            : nextLineAfter(lineStarts, wordStart,
                                    wordStart + Math.min(end - wordStart, fallbackDurationMs));
                    stop = Math.max(wordStart + 1, stop);
                    // A following timestamp bounds the segment even if its text
                    // contains several words. Do not expand it by sentence margins.
                    append(fullText, pieces, segmentText, wordStart, stop, !nextTimed, anchored);
                    if (!segmentText.isBlank()) firstWord = false;
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
            long from = Math.max(0, approximateStart(first, match.start(), shortEstimates) - beforeMs);
            long to = approximateEnd(last, match.end(), shortEstimates);
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
