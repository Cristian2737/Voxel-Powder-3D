/* GPL-3.0-or-later. Android-local YouTube caption acquisition; no external service. */
package app.morphe.extension.youtube.patches;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requests the web caption resource on this device. Android's native caption
 * resource can contain only sentences even when the web resource has word times.
 * A current player signature timestamp is necessary for a valid WEB player reply.
 * Nothing here plays media, reads browser cookies, or calls a third-party server.
 */
final class ProfanityWebCaptions {
    static final String ORIGIN = "https://www.youtube.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern VERSION = Pattern.compile(
            "\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final Pattern PLAYER = Pattern.compile(
            "\"(?:jsUrl|PLAYER_JS_URL)\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")");
    private static final Pattern STS = Pattern.compile(
            "(?:signatureTimestamp|sts)\\s*:\\s*([0-9]{5})\\b");
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long CONFIG_AGE_MS = 6 * 60 * 60 * 1000L;

    record Result(Document document, String language, String vss, int wordOffsets) {}
    private record Config(String version, long timestamp, long loadedAt) {}
    interface Transport {
        byte[] request(String url, String body) throws Exception;
    }

    private final Transport transport;
    private Config config;

    ProfanityWebCaptions() { this(ProfanityWebCaptions::request); }
    ProfanityWebCaptions(Transport transport) { this.transport = transport; }

    private static String literal(Matcher match) throws Exception {
        return new JSONArray("[" + match.group(1) + "]").getString(0);
    }

    private Config configuration(String video) throws Exception {
        if (config != null && System.currentTimeMillis() - config.loadedAt() < CONFIG_AGE_MS)
            return config;
        String html = text(transport.request(ORIGIN + "/watch?v=" + video, null));
        Matcher version = VERSION.matcher(html), player = PLAYER.matcher(html);
        if (!version.find() || !player.find()) throw new IOException("Player configuration absent");
        String clientVersion = literal(version);
        if (!clientVersion.matches("[0-9.]{5,40}")) throw new IOException("Invalid client version");
        URI playerUri = URI.create(ORIGIN).resolve(literal(player));
        if (!allowed(playerUri) || !playerUri.getPath().matches(
                "/s/player/[A-Za-z0-9_-]+/[A-Za-z0-9_./-]+\\.js")
                || playerUri.getPath().contains("..")) throw new IOException("Invalid player path");
        Matcher sts = STS.matcher(text(transport.request(playerUri.toString(), null)));
        if (!sts.find()) throw new IOException("Player timestamp absent");
        config = new Config(clientVersion, Long.parseLong(sts.group(1)), System.currentTimeMillis());
        return config;
    }

    /** Only small scalar configuration is cached, never player JS or signed URLs. */
    synchronized Result fetch(String video, String language) throws Exception {
        if (video == null || !video.matches("[A-Za-z0-9_-]{11}"))
            throw new IOException("Invalid video ID");
        String wanted = language == null || language.isEmpty() ? Locale.getDefault().getLanguage() : language;
        for (int attempt = 0; attempt < 2; attempt++) {
            Config current = configuration(video);
            JSONObject client = new JSONObject().put("clientName", "WEB")
                    .put("clientVersion", current.version()).put("clientScreen", "WATCH")
                    .put("platform", "DESKTOP").put("hl", "en").put("gl", "US")
                    .put("utcOffsetMinutes", 0);
            JSONObject context = new JSONObject().put("client", client)
                    .put("request", new JSONObject().put("internalExperimentFlags", new JSONArray())
                            .put("useSsl", true))
                    .put("user", new JSONObject().put("lockedSafetyMode", false));
            JSONObject visitor = post("visitor_id", new JSONObject().put("context", context));
            String visitorId = visitor.getJSONObject("responseContext").getString("visitorData");
            if (visitorId.isEmpty() || visitorId.length() > 4096) throw new IOException("Visitor unavailable");
            client.put("visitorData", visitorId);
            JSONObject response = post("player", new JSONObject().put("context", context)
                    .put("videoId", video).put("contentCheckOk", true).put("racyCheckOk", true)
                    .put("playbackContext", new JSONObject().put("contentPlaybackContext",
                            new JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
                                    .put("signatureTimestamp", current.timestamp()))));
            if (!"OK".equals(response.getJSONObject("playabilityStatus").optString("status"))) {
                config = null; // A player rollout may invalidate the cached timestamp.
                continue; // At most one fresh-config retry; never an unbounded request loop.
            }
            if (!video.equals(response.getJSONObject("videoDetails").optString("videoId")))
                throw new IOException("Caption video mismatch");
            JSONObject captions = response.optJSONObject("captions");
            if (captions == null) return null;
            JSONObject renderer = captions.optJSONObject("playerCaptionsTracklistRenderer");
            if (renderer == null) return null;
            JSONArray tracks = renderer.optJSONArray("captionTracks");
            JSONObject track = choose(tracks, wanted);
            if (track == null) return null;
            URI uri = URI.create(track.getString("baseUrl"));
            if (!allowed(uri) || !"/api/timedtext".equals(uri.getPath())
                    || !video.equals(parameter(uri.getRawQuery(), "v")))
                throw new IOException("Invalid caption resource");
            String url = withFormat(uri);
            Document document = CaptionXml.read(transport.request(url, null));
            if (document == null) return null;
            return new Result(document, track.optString("languageCode"), track.optString("vssId"),
                    wordOffsets(document));
        }
        return null;
    }

    static int wordOffsets(Document document) {
        NodeList segments = document.getElementsByTagName("s");
        int offsets = 0;
        for (int i = 0; i < segments.getLength(); i++) {
            Element segment = (Element) segments.item(i);
            if (segment.hasAttribute("t") && !segment.getTextContent().isBlank()) offsets++;
        }
        return offsets;
    }

    private JSONObject post(String endpoint, JSONObject data) throws Exception {
        return new JSONObject(text(transport.request(ORIGIN + "/youtubei/v1/" + endpoint
                + "?prettyPrint=false", data.toString())));
    }

    private static JSONObject choose(JSONArray tracks, String language) throws Exception {
        if (tracks == null || tracks.length() > 200) return null;
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.getJSONObject(i);
            String code = track.optString("languageCode");
            int score = code.equalsIgnoreCase(language) ? 4
                    : code.split("-")[0].equalsIgnoreCase(language.split("-")[0]) ? 2 : 0;
            if (score == 0) continue; // Do not substitute a different spoken language.
            if ("asr".equals(track.optString("kind"))) score++;
            if (score > bestScore) { best = track; bestScore = score; }
        }
        return best;
    }

    private static String parameter(String query, String key) throws Exception {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0]))
                return java.net.URLDecoder.decode(pair[1], "UTF-8");
        }
        return "";
    }

    private static String withFormat(URI uri) {
        StringBuilder query = new StringBuilder();
        for (String part : uri.getRawQuery().split("&")) {
            String key = part.split("=", 2)[0];
            if (key.equals("fmt") || key.equals("tlang") || key.equals("xorb")
                    || key.equals("xobt") || key.equals("xovt")) continue;
            if (query.length() > 0) query.append('&');
            query.append(part); // Preserve signed parameter encoding exactly.
        }
        return ORIGIN + "/api/timedtext?" + query + "&fmt=srv3&xorb=2&xobt=3&xovt=3";
    }

    private static boolean allowed(URI uri) {
        return "https".equals(uri.getScheme()) && "www.youtube.com".equals(uri.getHost())
                && uri.getRawUserInfo() == null && uri.getPort() == -1 && uri.getFragment() == null;
    }

    private static String text(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_BYTES)
            throw new IOException("Empty or oversized caption response");
        return new String(data, StandardCharsets.UTF_8);
    }

    private static byte[] request(String address, String body) throws Exception {
        if (!allowed(URI.create(address))) throw new IOException("Unsupported caption host");
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Referer", ORIGIN + "/");
        connection.setRequestProperty("Origin", ORIGIN);
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                JSONObject client = new JSONObject(body).getJSONObject("context").getJSONObject("client");
                connection.setRequestProperty("X-YouTube-Client-Name", "1");
                connection.setRequestProperty("X-YouTube-Client-Version", client.getString("clientVersion"));
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            if (connection.getResponseCode() != 200) throw new IOException("Caption request unavailable");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Caption response too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) {
                    if (output.size() + count > MAX_BYTES) throw new IOException("Caption response too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }
}
