/* GPL-3.0-or-later. Experimental caption-synchronised muting for Morphe YouTube. */
package app.morphe.extension.youtube.patches;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.VideoState;

/** An opt-in, local-only filter. Unavailable captions fail open rather than muting whole videos. */
@SuppressWarnings("unused")
public final class ProfanityFilterPatch {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern XML_TEXT = Pattern.compile(">([^<>]+)<", Pattern.DOTALL);
    private static final long SAMPLE_INTERVAL_MS = 35L;
    private static volatile String currentVideo = "";
    private static volatile List<StableCaptionBlockPolicy.CensorWindow> windows = Collections.emptyList();
    private static boolean scheduled;
    private static String cachedKey;
    private static Pattern cachedPattern;

    private ProfanityFilterPatch() {}

    public static boolean isEnabled() {
        return Settings.PROFANITY_FILTER.get();
    }

    static synchronized Pattern rules() {
        String key = Settings.PROFANITY_DEFAULT_WORDS.get() + "|"
                + Settings.PROFANITY_REVERENT_PHRASES.get() + "|"
                + Settings.PROFANITY_CUSTOM_WORDS.get();
        if (cachedPattern == null || !key.equals(cachedKey)) {
            cachedKey = key;
            cachedPattern = ProfanityRules.compile(Settings.PROFANITY_DEFAULT_WORDS.get(),
                    Settings.PROFANITY_REVERENT_PHRASES.get(), Settings.PROFANITY_CUSTOM_WORDS.get());
        }
        return cachedPattern;
    }

    /** Bound to native video-ID hook, not a caption callback that may arrive late. */
    public static void onVideoId(@Nullable String id) {
        Utils.runOnMainThreadNowOrLater(() -> {
            String next = id == null ? "" : id;
            if (!next.equals(currentVideo)) {
                currentVideo = next;
                windows = Collections.emptyList();
                PlayerVolumePatch.setProfanityMuted(false);
            }
        });
    }

    /** A user turning native subtitles off must not leave the last word list active. */
    public static void captionsDisabled() {
        Utils.runOnMainThreadNowOrLater(() -> {
            windows = Collections.emptyList();
            PlayerVolumePatch.setProfanityMuted(false);
        });
    }

    static void onCaptionSource(String videoId, StableCaptionBlockPolicy policy) {
        if (!isEnabled() || videoId == null || policy == null) return;
        List<StableCaptionBlockPolicy.CensorWindow> prepared = policy.censorWindows(rules());
        Utils.runOnMainThreadNowOrLater(() -> {
            if (!videoId.equals(currentVideo) || !isEnabled()) return;
            windows = prepared;
            sample();
            schedule();
        });
    }

    /** Video time hook; also wakes the 35ms clock sampler after seek / resume. */
    public static void videoTimeChanged(long timeMs) {
        if (!isEnabled()) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        sample();
        schedule();
    }

    private static void schedule() {
        if (scheduled || !isEnabled() || windows.isEmpty()
                || VideoState.getCurrent() != VideoState.PLAYING) return;
        scheduled = true;
        MAIN.postDelayed(() -> {
            scheduled = false;
            sample();
            schedule();
        }, SAMPLE_INTERVAL_MS);
    }

    private static void sample() {
        if (!isEnabled() || windows.isEmpty()
                || VideoState.getCurrent() != VideoState.PLAYING
                || !currentVideo.equals(VideoInformation.getVideoId())) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        long time = VideoInformation.getVideoTime();
        if (time < 0) {
            PlayerVolumePatch.setProfanityMuted(false);
            return;
        }
        int low = 0, high = windows.size() - 1, candidate = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (windows.get(middle).start <= time) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        PlayerVolumePatch.setProfanityMuted(candidate >= 0 && time < windows.get(candidate).end);
    }

    /** Edits the original caption XML after original word timings have been captured. */
    static byte[] censorCaptionBytes(byte[] input) {
        if (!isEnabled() || !Settings.PROFANITY_CENSOR_CAPTIONS.get() || input == null) return input;
        String xml = new String(input, StandardCharsets.UTF_8);
        Matcher m = XML_TEXT.matcher(xml);
        Pattern rule = rules();
        StringBuilder out = null;
        int cursor = 0;
        while (m.find()) {
            String original = m.group(1);
            String replaced = ProfanityRules.censor(original, rule);
            if (original.equals(replaced)) continue;
            if (out == null) out = new StringBuilder(xml.length() + 32);
            out.append(xml, cursor, m.start(1)).append(replaced);
            cursor = m.end(1);
        }
        if (out == null) return input;
        out.append(xml, cursor, xml.length());
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}