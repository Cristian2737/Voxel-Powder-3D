/* GPL-3.0-or-later. Experimental caption-synchronised muting for Morphe YouTube. */
package app.morphe.extension.youtube.patches;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.VideoState;

/**
 * Local caption-based profanity filter, independent of stable-caption-block injection.
 * Opt-in. Invalid, absent or late transcripts fail open rather than muting the entire video.
 */
@SuppressWarnings("unused")
public final class ProfanityFilterPatch {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long TICK_MS = 35;
    private static final int MAX_PARAGRAPHS = 30000;
    private static final int MAX_SEGMENTS = 100000;
    private static final Pattern XML_TEXT = Pattern.compile(">([^<>]+)<", Pattern.DOTALL);
    private static volatile String currentVideo = "";
    private static volatile TrackStamp selectedTrack;
    private static final ThreadLocal<TrackStamp> parsedTrack = new ThreadLocal<>();
    private static volatile List<Window> windows = Collections.emptyList();
    private static String cachedRulesKey;
    private static Pattern cachedRules;
    private static boolean tickScheduled;

    private record TrackStamp(String video, String vss, String language) {}
    private record Window(long start, long end) {}
    private record Piece(int startCharacter, int endCharacter, long startTime, long endTime) {}

    private ProfanityFilterPatch() {}

    public static boolean isEnabled() {
        return Settings.PROFANITY_FILTER.get();
    }

    private static synchronized Pattern rules() {
        String key = Settings.PROFANITY_DEFAULT_WORDS.get() + "|"
                + Settings.PROFANITY_REVERENT_PHRASES.get() + "|"
                + Settings.PROFANITY_CUSTOM_WORDS.get();
        if (cachedRules == null || !key.equals(cachedRulesKey)) {
            cachedRulesKey = key;
            cachedRules = ProfanityRules.compile(Settings.PROFANITY_DEFAULT_WORDS.get(),
                    Settings.PROFANITY_REVERENT_PHRASES.get(), Settings.PROFANITY_CUSTOM_WORDS.get());
        }
        return cachedRules;
    }

    @Nullable
    private static TrackStamp stamp(@Nullable Object candidate) {
        if (!(candidate instanceof AutoTranslateCaptionsPatch.CaptionTrackInterface track)) return null;
        try {
            Map<String,String> params = AutoTranslateCaptionsPatch.captionParameters(track.patch_getUrl());
            if (params == null) return null;
            String video = params.get("v"), vss = track.patch_getVssId();
            if (video == null || !video.matches("[A-Za-z0-9_-]{11}") || vss == null
                    || vss.isEmpty()) return null;
            String language = track.patch_getLanguageCode();
            return new TrackStamp(video, vss, language == null ? "" : language);
        } catch (RuntimeException | LinkageError error) {
            Logger.printException(() -> "Profanity caption metadata not available", error);
            return null;
        }
    }

    /** Called from preferred-caption native selection hooks. */
    public static void onCaptionSelected(@Nullable Object candidate) {
        if (!isEnabled()) return;
        TrackStamp next = stamp(candidate);
        Utils.runOnMainThreadNowOrLater(() -> {
            if (next == null || !next.equals(selectedTrack)) {
                selectedTrack = next;
                windows = Collections.emptyList();
                PlayerVolumePatch.setProfanityMuted(false);
            }
        });
    }

    /** Called by the source video ID hook. */
    public static void onVideoId(@Nullable String id) {
        Utils.runOnMainThreadNowOrLater(() -> {
            String video = id == null ? "" : id;
            if (video.equals(currentVideo)) return;
            currentVideo = video;
            if (selectedTrack == null || !video.equals(selectedTrack.video())) selectedTrack = null;
            windows = Collections.emptyList();
            PlayerVolumePatch.setProfanityMuted(false);
        });
    }

    /** Capture the current parser thread's request before reading its caption bytes. */
    public static void setParsedTrack(@Nullable Object candidate) {
        parsedTrack.remove();
        if (isEnabled()) {
            TrackStamp stamp = stamp(candidate);
            if (stamp != null) parsedTrack.set(stamp);
        }
    }

    /** Invoked after safe XML parsing, before optional subtitle-text edits. */
    public static void captureCaptionDocument(@Nullable Document document) {
        TrackStamp stamp = parsedTrack.get();
        parsedTrack.remove();
        if (!isEnabled() || stamp == null || document == null) return;
        try {
            List<Window> extracted = parseWindows(document, rules());
            Utils.runOnMainThreadNowOrLater(() -> {
                if (!isEnabled() || !stamp.video().equals(currentVideo)
                        || !stamp.equals(selectedTrack)) return;
                windows = extracted;
                updateMute();
                schedule();
            });
        } catch (RuntimeException error) {
            Logger.printException(() -> "Profanity caption timing failed", error);
        }
    }

    private static long millis(String raw, long fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Build word-level intervals when available, otherwise use the containing paragraph. */
    private static List<Window> parseWindows(Document doc, Pattern pattern) {
        NodeList paragraphs = doc.getElementsByTagName("p");
        if (paragraphs.getLength() > MAX_PARAGRAPHS) return Collections.emptyList();
        ArrayList<Window> found = new ArrayList<>();
        int segmentsSeen = 0;
        for (int p = 0; p < paragraphs.getLength(); p++) {
            if (!(paragraphs.item(p) instanceof Element element)) continue;
            long from = millis(element.getAttribute("t"), -1);
            long duration = millis(element.getAttribute("d"), -1);
            if (from < 0 || duration <= 0 || duration > 600000 || from > Long.MAX_VALUE - duration)
                continue;
            long end = from + duration;
            NodeList segments = element.getElementsByTagName("s");
            segmentsSeen += segments.getLength();
            if (segmentsSeen > MAX_SEGMENTS) return Collections.emptyList();

            if (segments.getLength() == 0) {
                if (ProfanityRules.contains(element.getTextContent(), pattern))
                    found.add(new Window(Math.max(0, from - 150), end + 180));
                continue;
            }

            StringBuilder full = new StringBuilder();
            ArrayList<Piece> pieces = new ArrayList<>();
            for (int i = 0; i < segments.getLength(); i++) {
                Element segment = (Element) segments.item(i);
                long offset = millis(segment.getAttribute("t"), 0);
                if (offset < 0 || offset >= duration) continue;
                long nextOffset = duration;
                if (i + 1 < segments.getLength()) {
                    nextOffset = millis(((Element) segments.item(i + 1)).getAttribute("t"), duration);
                }
                long segmentEnd = from + Math.max(offset + 1, Math.min(duration, nextOffset));
                String text = segment.getTextContent();
                int characterStart = full.length();
                full.append(text);
                pieces.add(new Piece(characterStart, full.length(), from + offset, segmentEnd));
            }
            Matcher matches = pattern.matcher(full);
            while (matches.find()) {
                long first = Long.MAX_VALUE, last = -1;
                for (Piece piece : pieces) {
                    if (piece.endCharacter() <= matches.start()
                            || piece.startCharacter() >= matches.end()) continue;
                    first = Math.min(first, piece.startTime());
                    last = Math.max(last, piece.endTime());
                }
                if (last > first) found.add(new Window(Math.max(0, first - 150), last + 180));
            }
        }
        if (found.isEmpty()) return Collections.emptyList();
        found.sort(Comparator.comparingLong(Window::start));
        ArrayList<Window> merged = new ArrayList<>();
        Window active = found.get(0);
        for (int i = 1; i < found.size(); i++) {
            Window next = found.get(i);
            if (next.start() <= active.end()) {
                active = new Window(active.start(), Math.max(active.end(), next.end()));
            } else {
                merged.add(active);
                active = next;
            }
        }
        merged.add(active);
        return Collections.unmodifiableList(merged);
    }

    /** Reuse existing XML validation, avoid editing markup/attributes. */
    public static byte[] censorCaptionBytes(byte[] original) {
        if (!isEnabled() || !Settings.PROFANITY_CENSOR_CAPTIONS.get() || original == null) return original;
        String xml = new String(original, StandardCharsets.UTF_8);
        Pattern pattern = rules();
        Matcher matcher = XML_TEXT.matcher(xml);
        StringBuilder out = null;
        int from = 0;
        while (matcher.find()) {
            String before = matcher.group(1);
            String after = ProfanityRules.censor(before, pattern);
            if (before.equals(after)) continue;
            if (out == null) out = new StringBuilder(xml.length() + 64);
            out.append(xml, from, matcher.start(1)).append(after);
            from = matcher.end(1);
        }
        if (out == null) return original;
        return out.append(xml, from, xml.length()).toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Called by playback clock; the follow-up clock helps keep short mute windows accurate. */
    public static void videoTimeChanged(long timeMs) {
        updateMute();
        schedule();
    }

    private static void schedule() {
        if (tickScheduled || !isEnabled() || windows.isEmpty()
                || VideoState.getCurrent() != VideoState.PLAYING) return;
        tickScheduled = true;
        MAIN.postDelayed(() -> {
            tickScheduled = false;
            updateMute();
            schedule();
        }, TICK_MS);
    }

    private static void updateMute() {
        if (!isEnabled() || windows.isEmpty()
                || VideoState.getCurrent() != VideoState.PLAYING
                || !currentVideo.equals(VideoInformation.getVideoId())) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        long time = VideoInformation.getVideoTime();
        int low = 0, high = windows.size() - 1, candidate = -1;
        while (low <= high) {
            int i = (low + high) >>> 1;
            if (windows.get(i).start() <= time) {
                candidate = i;
                low = i + 1;
            } else high = i - 1;
        }
        PlayerVolumePatch.setProfanityMuted(candidate >= 0 && time >= 0
                && time < windows.get(candidate).end());
    }
}
