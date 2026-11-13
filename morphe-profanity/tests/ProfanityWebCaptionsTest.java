package app.morphe.extension.youtube.patches;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Protocol contracts use invented captions and fake transport, never browser exports. */
public final class ProfanityWebCaptionsTest {
    private static int checks;
    private static final String VIDEO = "abcdefghijk";
    private static final String XML = "<timedtext format=\"3\"><body><p t=\"1000\" d=\"2000\">"
            + "<s>well </s><s t=\"500\">shit </s><s t=\"900\">okay</s></p></body></timedtext>";
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static class Fake implements ProfanityWebCaptions.Transport {
        final List<String> urls = new ArrayList<>();
        String video = VIDEO, host = "https://www.youtube.com", status = "OK";
        String xml = XML, js = "signatureTimestamp:20767";
        boolean captions = true;
        public byte[] request(String url, String body) throws Exception {
            urls.add(url);
            String result;
            if (url.contains("/watch?")) {
                result = "{\"INNERTUBE_CLIENT_VERSION\":\"2.20261112.00.00\","
                        + "\"jsUrl\":\"/s/player/test/player_es6.vflset/en_US/base.js\"}";
            } else if (url.contains("/s/player/")) result = js;
            else if (url.contains("/visitor_id?")) {
                check(!new JSONObject(body).getJSONObject("context").getJSONObject("client")
                        .has("visitorData"), "fresh anonymous visitor request");
                result = "{\"responseContext\":{\"visitorData\":\"fixture-visitor\"}}";
            } else if (url.contains("/player?")) {
                JSONObject request = new JSONObject(body);
                check(request.getJSONObject("playbackContext").getJSONObject("contentPlaybackContext")
                        .getLong("signatureTimestamp") == 20767, "current timestamp accompanies player request");
                check(request.getJSONObject("context").getJSONObject("client").getString("visitorData")
                        .equals("fixture-visitor"), "same-request visitor supplied");
                result = "{\"playabilityStatus\":{\"status\":\"" + status + "\"},"
                        + "\"videoDetails\":{\"videoId\":\"" + video + "\"}"
                        + (captions ? ",\"captions\":{\"playerCaptionsTracklistRenderer\":{\"captionTracks\":["
                        + "{\"baseUrl\":\"" + host + "/api/timedtext?v=" + VIDEO
                        + "&signature=fake%2Bsignature&fmt=json3\",\"languageCode\":\"en\","
                        + "\"vssId\":\"a.en\",\"kind\":\"asr\"}]}}" : "") + "}";
            } else if (url.contains("/api/timedtext?")) {
                check(url.contains("signature=fake%2Bsignature"), "signed parameter bytes preserved");
                check(url.contains("fmt=srv3") && !url.contains("fmt=json3"), "single SRV3 format");
                result = xml;
            } else throw new AssertionError("Unexpected fixture request");
            return result.getBytes(StandardCharsets.UTF_8);
        }
    }
    private static void fails(Fake transport, String message) throws Exception {
        try { new ProfanityWebCaptions(transport).fetch(VIDEO, "en"); }
        catch (Exception expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--live")) {
            var result = new ProfanityWebCaptions().fetch("8_vTxsB3IYA", "en");
            check(result != null && result.wordOffsets() > 1000, "live word timing must exist");
            var scan = ProfanitySchedule.scan(result.document(), ProfanityRules.compile(true,true,"","",true),150,1500,true);
            System.out.println("LIVE HOST ONLY: offsets=" + result.wordOffsets() + ", segments="
                    + scan.segmentCount() + ", matches=" + scan.matchCount() + ", windows=" + scan.windows().size());
            return;
        }
        Fake fake = new Fake();
        var provider = new ProfanityWebCaptions(fake);
        var result = provider.fetch(VIDEO, "en");
        check(result != null && result.wordOffsets() == 2, "relative word offsets retained");
        check(result.document().getElementsByTagName("s").item(1).getTextContent().equals("shit "),
                "caption text retained without AI or transformation");
        provider.fetch(VIDEO, "en");
        check(fake.urls.stream().filter(u -> u.contains("/s/player/")).count() == 1,
                "player JS fetched once per configuration cache lifetime");
        check(fake.urls.size() == 8, "five initial requests, three warm requests");
        fake = new Fake(); fake.video = "lmnopqrstuv"; fails(fake,"reject a different returned video");
        check(fake.urls.stream().noneMatch(u -> u.contains("/api/timedtext?")), "mismatch never fetches caption URL");
        fake = new Fake(); fake.host = "https://example.invalid"; fails(fake,"reject third-party caption URL");
        fake = new Fake(); fake.captions = false;
        check(new ProfanityWebCaptions(fake).fetch(VIDEO,"en") == null,"no-caption video fails open");
        fake = new Fake();
        check(new ProfanityWebCaptions(fake).fetch(VIDEO,"fr") == null,"do not filter wrong caption language");
        fake = new Fake(); fake.status = "UNPLAYABLE";
        check(new ProfanityWebCaptions(fake).fetch(VIDEO,"en") == null,"unplayable fails open");
        check(fake.urls.size() == 8,"at most one fresh-config retry");
        fake = new Fake(); fake.js = "no timestamp"; fails(fake,"missing timestamp cannot create bogus request");
        fake = new Fake(); fake.xml = "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///private'>]><timedtext format=\"3\"/>";
        check(new ProfanityWebCaptions(fake).fetch(VIDEO,"en") == null,"reject caption external entities");
        fake = new Fake();
        try { new ProfanityWebCaptions(fake).fetch("bad/id", "en"); throw new AssertionError("bad ID accepted"); }
        catch (java.io.IOException expected) { check(fake.urls.isEmpty(),"invalid ID makes zero requests"); }
        System.out.println("PASS: " + checks + " web-caption acquisition contracts (host, no device playback)");
    }
}
