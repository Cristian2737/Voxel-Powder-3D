package app.morphe.extension.youtube.patches;

import android.media.AudioTrack;
import app.morphe.extension.youtube.settings.Settings;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/** Host integration checks. No VideoState double: the overlay is deliberately absent. */
public final class ProfanityRuntimeTest {
    private static int checks;
    private static AudioTrack track = new AudioTrack();
    private static void volume(float expected, String message) {
        checks++;
        if (Math.abs(track.volume - expected) > .00001f)
            throw new AssertionError(message + ": " + track.volume + " != " + expected);
    }
    private static void at(long time) {
        VideoInformation.time = time;
        ProfanityFilterPatch.videoTimeChanged(time);
    }
    private record Track(String video) implements AutoTranslateCaptionsPatch.CaptionTrackInterface {
        public String patch_getUrl() { return "https://www.youtube.com/api/timedtext?v=" + video; }
        public String patch_getVssId() { return "a.en"; }
        public String patch_getLanguageCode() { return "en"; }
    }
    private static void capture(String video) throws Exception {
        ProfanityFilterPatch.setParsedTrack(new Track(video));
        var parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        ProfanityFilterPatch.captureCaptionDocument(parser.parse(new InputSource(new StringReader(
            "<timedtext><body><p t=\"1000\" d=\"2000\"><s t=\"0\">well </s>"
            + "<s t=\"500\">shit </s><s t=\"900\">okay</s></p></body></timedtext>"))));
    }
    public static void main(String[] args) throws Exception {
        PlayerVolumePatch.getAudioMultiplier(.42f);
        PlayerVolumePatch.setAudioTrack(track);
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        capture(VideoInformation.video);
        at(1349); volume(.42f, "normal volume before lead-in");
        at(1350); volume(0, "mute at exact start without any control-overlay VideoState");
        at(1819); volume(0, "mute until the word ends");
        at(1820); volume(.42f, "restore at exact word end before trailing space");
        at(1500); volume(0, "backward seek into profanity");
        track.state=2; at(1500); volume(.42f, "paused audio cannot leave filter latched");
        track.state=3; at(1500); volume(0, "resume inside a word remutes");
        at(100); volume(.42f, "seek outside window restores");
        PlayerVolumePatch.setMuted(true);
        at(1500); at(2000); volume(0, "finishing a filter window preserves user mute");
        PlayerVolumePatch.setMuted(false); volume(.42f, "user unmute restores original base volume");
        PlayerVolumePatch.setDuckMultiplier(.3f);
        at(1500); volume(0, "filter takes priority over ducking");
        at(2000); volume(.126f, "restores ducked volume after profanity");
        PlayerVolumePatch.clearDuckMultiplier();
        VideoInformation.video="lmnopqrstuv";
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        at(1500); volume(.42f, "new video cannot inherit previous transcript");
        capture("abcdefghijk"); at(1500); volume(.42f, "background captions cannot mute current video");
        capture(VideoInformation.video); at(1500); volume(0, "current video captions activate");
        Settings.PROFANITY_NEVER_CHANNELS.save("ibz");
        at(1500); volume(.42f, "never-censor channel wins");
        Settings.PROFANITY_ALWAYS_CHANNELS.save("channel-1");
        at(1500); volume(.42f, "never-censor still wins over always-censor");
        Settings.PROFANITY_NEVER_CHANNELS.save("");
        Settings.PROFANITY_FILTER.save(false);
        at(1500); volume(0, "always-censor overrides global off");
        Settings.PROFANITY_ALWAYS_CHANNELS.save("");
        at(1500); volume(.42f, "global off immediately releases filter mute");
        Settings.PROFANITY_FILTER.save(true);
        VideoInformation.video="wxyzABCDEFG";
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        Settings.PROFANITY_TEST_AUDIO.save(true);
        at(1000); volume(0, "audio self-test works without captions or control-overlay state");
        at(5000); volume(.42f, "self-test restores at five seconds");
        track.state=1; at(1000); volume(.42f, "stopped track cannot start filter mute");
        track.state=3;
        Settings.PROFANITY_TEST_AUDIO.save(false);
        byte[] source="<p title=\"shit\">shit</p>".getBytes(StandardCharsets.UTF_8);
        String masked=new String(ProfanityFilterPatch.censorCaptionBytes(source), StandardCharsets.UTF_8);
        checks++;
        if (!masked.equals("<p title=\"shit\">****</p>")) throw new AssertionError("mask text, preserve XML attributes");
        var observerField = ProfanityFilterPatch.class.getDeclaredField("captionObserver");
        observerField.setAccessible(true);
        int[] observed = {0};
        observerField.set(null, (java.util.function.BiConsumer<org.w3c.dom.Document,String>)(document, url) -> {
            if (!document.getDocumentElement().getTextContent().contains("shit")
                    || url == null || !url.endsWith(VideoInformation.video)) throw new AssertionError("original caption observation");
            observed[0]++;
        });
        capture(VideoInformation.video);
        checks++;
        if (observed[0] != 1) throw new AssertionError("observer must receive original document once");
        ProfanityFilterPatch.captureCaptionDocument(null);
        observerField.set(null, null);
        checks++;
        if (observed[0] != 1) throw new AssertionError("observer request state must be cleared");
        // New acquisition is independent of CC selection, bounded, and cannot
        // apply a completed request to a different foreground video.
        var background = app.morphe.extension.shared.Utils.background;
        background.clear();
        var running = ProfanityFilterPatch.class.getDeclaredField("webFetchRunning");
        running.setAccessible(true); running.setBoolean(null, false);
        VideoInformation.video="webcaption1";
        var parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        var rich = parser.parse(new InputSource(new StringReader(
            "<timedtext><body><p t=\"1000\" d=\"2000\"><s t=\"0\">well </s>"
            + "<s t=\"500\">shit </s><s t=\"900\">okay</s></p></body></timedtext>")));
        ProfanityWebCaptions.result = new ProfanityWebCaptions.Result(rich,"en","a.en",3);
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        for (int i=0;i<20;i++) at(1500);
        checks++; if (background.size()!=1) throw new AssertionError("only one caption acquisition worker");
        background.remove(0).run();
        at(1500); volume(0,"web caption acquisition works with CC never selected");
        ProfanityFilterPatch.setParsedTrack(new Track(VideoInformation.video));
        ProfanityFilterPatch.captureCaptionDocument(parser.parse(new InputSource(new StringReader(
            "<timedtext><body><p t=\"8000\" d=\"2000\">shit safe words</p></body></timedtext>"))));
        at(8100); volume(.42f,"sentence-only native response cannot overwrite richer timing");
        at(1500); volume(0,"richer schedule retained after native response");
        checks++; if (!background.isEmpty()) throw new AssertionError("warm timed captions must not refetch");
        VideoInformation.video="webcaption2";
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        VideoInformation.video="webcaption3";
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        background.remove(0).run();
        at(1500); volume(.42f,"late web result cannot mute the next video");
        checks++; if (background.size()!=1) throw new AssertionError("only latest video follows completed fetch");
        Settings.PROFANITY_WEB_TIMING.save(false);
        background.remove(0).run();
        at(1500); volume(.42f,"turning web timing off rejects its in-flight result");
        Settings.PROFANITY_WEB_TIMING.save(true);
        Settings.PROFANITY_NEVER_CHANNELS.save("ibz");
        VideoInformation.video="webcaption4";
        ProfanityFilterPatch.onVideoId(VideoInformation.video);
        checks++; if (!background.isEmpty()) throw new AssertionError("whitelisted channel makes no web request");
        System.out.println("PASS: " + checks + " runtime lifecycle and volume checks (host doubles, not a device test)");
    }
}
