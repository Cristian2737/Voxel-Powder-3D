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
    private static final Pattern XML_TEXT = Pattern.compile(">([^<>]+)<", Pattern.DOTALL);
    private static volatile String currentVideo = "";
    private static volatile TrackStamp selectedTrack;
    private static final ThreadLocal<TrackStamp> parsedTrack = new ThreadLocal<>();
    // Captions may finish parsing before the player video ID and selection hooks fire.
    // Retain the latest transcript briefly so event ordering does not discard it.
    private static volatile TrackStamp latestCaptionTrack;
    private static volatile List<Window> latestCaptionWindows = Collections.emptyList();
    private static volatile List<Window> windows = Collections.emptyList();
    private static String cachedRulesKey;
    private static Pattern cachedRules;
    private static boolean tickScheduled;

    private record TrackStamp(String video, String vss, String language) {}
    private record Window(long start, long end) {}

    private ProfanityFilterPatch() {}

    /**
     * Channel overrides use exact channel IDs or names, one per line or comma.
     * Never-censor wins over always-censor when both lists contain a channel.
     */
    private static boolean containsChannel(String entries) {
        if (entries == null || entries.isBlank()) return false;
        String id = VideoInformation.getChannelId();
        String name = VideoInformation.getChannelName();
        for (String raw : entries.split("[,;\\r\\n]+")) {
            String entry = raw.strip();
            if (entry.isEmpty()) continue;
            if (id != null && entry.equalsIgnoreCase(id)) return true;
            if (name != null && entry.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static boolean isEnabled() {
        if (containsChannel(Settings.PROFANITY_NEVER_CHANNELS.get())) return false;
        return Settings.PROFANITY_FILTER.get()
                || containsChannel(Settings.PROFANITY_ALWAYS_CHANNELS.get());
    }

    private static long muteBufferMs() {
        try {
            return Math.max(0, Math.min(500,
                    Long.parseLong(Settings.PROFANITY_MUTE_BUFFER.get().strip())));
        } catch (RuntimeException ignored) {
            return 150;
        }
    }

    private static long muteDurationMs() {
        try {
            return Math.max(100, Math.min(5000,
                    Long.parseLong(Settings.PROFANITY_MUTE_DURATION.get().strip())));
        } catch (RuntimeException ignored) {
            return 1500;
        }
    }

    private static synchronized Pattern rules() {
        String key = Settings.PROFANITY_DEFAULT_WORDS.get() + "|"
                + Settings.PROFANITY_REVERENT_PHRASES.get() + "|"
                + Settings.PROFANITY_CUSTOM_WORDS.get() + "|"
                + Settings.PROFANITY_CUSTOM_REGEX.get() + "|"
                + Settings.PROFANITY_WORD_BOUNDARY.get();
        if (cachedRules == null || !key.equals(cachedRulesKey)) {
            cachedRulesKey = key;
            cachedRules = ProfanityRules.compile(Settings.PROFANITY_DEFAULT_WORDS.get(),
                    Settings.PROFANITY_REVERENT_PHRASES.get(), Settings.PROFANITY_CUSTOM_WORDS.get(),
                    Settings.PROFANITY_CUSTOM_REGEX.get(), Settings.PROFANITY_WORD_BOUNDARY.get());
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
            if (next != null && next.equals(selectedTrack)) return;
            selectedTrack = next;
            // Native selection and caption parse callbacks can arrive in either order;
            // the selected track can also be translated from the source captions.
            windows = next != null && latestCaptionTrack != null
                    && next.video().equals(latestCaptionTrack.video())
                    ? latestCaptionWindows : Collections.emptyList();
            PlayerVolumePatch.setProfanityMuted(false);
            updateMute();
            schedule();
        });
    }

    /** Called by the source video ID hook. */
    public static void onVideoId(@Nullable String id) {
        Utils.runOnMainThreadNowOrLater(() -> {
            String video = id == null ? "" : id;
            if (video.equals(currentVideo)) return;
            currentVideo = video;
            if (selectedTrack != null && !video.equals(selectedTrack.video())) selectedTrack = null;
            windows = latestCaptionTrack != null && video.equals(latestCaptionTrack.video())
                    ? latestCaptionWindows : Collections.emptyList();
            PlayerVolumePatch.setProfanityMuted(false);
            updateMute();
            schedule();
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
            ProfanitySchedule.Scan scanned = ProfanitySchedule.scan(
                    document, rules(), muteBufferMs(), muteDurationMs());
            List<Window> extracted = new ArrayList<>();
            for (ProfanitySchedule.Window w : scanned.windows()) {
                extracted.add(new Window(w.start(), w.end()));
            }
            extracted = Collections.unmodifiableList(extracted);
            final List<Window> compiledWindows = extracted;
            Utils.runOnMainThreadNowOrLater(() -> {
                if (!isEnabled()) return;
                // Original and translated tracks sometimes have different VSS IDs.
                // Filter the currently playing video by ID instead of requiring exact
                // caption-track object equality, which discarded real captions.
                latestCaptionTrack = stamp;
                latestCaptionWindows = compiledWindows;
                boolean playingVideo = stamp.video().equals(currentVideo)
                        || stamp.video().equals(VideoInformation.getVideoId());
                if (!playingVideo) return;
                windows = compiledWindows;
                updateMute();
                schedule();
                if (Settings.PROFANITY_DIAGNOSTICS.get()) {
                    Utils.showToastShort("Profanity filter: " + scanned.matchCount()
                            + " words/phrases, " + scanned.windows().size()
                            + " mute windows in " + scanned.captionCount() + " captions");
                }
            });
        } catch (RuntimeException error) {
            Logger.printException(() -> "Profanity caption timing failed", error);
        }
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
        if (tickScheduled || !isEnabled()
                || VideoState.getCurrent() != VideoState.PLAYING) return;
        String video = VideoInformation.getVideoId();
        boolean transcriptForCurrentVideo = video != null && latestCaptionTrack != null
                && video.equals(latestCaptionTrack.video()) && !latestCaptionWindows.isEmpty();
        if (!transcriptForCurrentVideo && !Settings.PROFANITY_TEST_AUDIO.get()) return;
        tickScheduled = true;
        MAIN.postDelayed(() -> {
            tickScheduled = false;
            updateMute();
            schedule();
        }, TICK_MS);
    }

    private static void updateMute() {
        String video = VideoInformation.getVideoId();
        if (!isEnabled()
                || VideoState.getCurrent() != VideoState.PLAYING
                || video == null || video.isEmpty()) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        long time = VideoInformation.getVideoTime();
        // Test mode does not require captions or the separate onVideoId hook.
        if (Settings.PROFANITY_TEST_AUDIO.get() && time >= 0 && time < 5000) {
            PlayerVolumePatch.setProfanityMuted(true);
            return;
        }
        // Caption requests can complete before or after selection / video-ID hooks.
        // Resynchronize on the actual playing video rather than stale callback state.
        TrackStamp track = latestCaptionTrack;
        if (track == null || !video.equals(track.video()) || time < 0) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        List<Window> currentWindows = latestCaptionWindows;
        if (currentWindows.isEmpty()) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        int low = 0, high = currentWindows.size() - 1, candidate = -1;
        while (low <= high) {
            int i = (low + high) >>> 1;
            if (currentWindows.get(i).start() <= time) {
                candidate = i;
                low = i + 1;
            } else high = i - 1;
        }
        PlayerVolumePatch.setProfanityMuted(candidate >= 0
                && time < currentWindows.get(candidate).end());
    }
}
