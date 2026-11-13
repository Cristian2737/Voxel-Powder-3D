# Android caption timing and existing-patch review

Checked November 13, 2026. Consumer requirement: install and use the patch entirely
on Android. No PC export, cookie copying, relay, subscription, or speech model.

## Working request prototype

For `8_vTxsB3IYA`, Android's native resource supplied 284 sentence blocks and zero
word-offset segments. A fresh, anonymous WEB resource requested with the **current
player signature timestamp** supplied 3,263 word offsets. The actual Java fetcher
retrieved 3,810 XML segments and generated 20 matches / 20 mute windows in a live
host test. This uses standard Android HTTPS and its built-in JSON/XML parsers;
there is no hidden browser, token generator, external backend, or desktop import.

The current player version/timestamp are discovered from YouTube and cached for
six hours. Acquisition is asynchronous, with one worker, a bounded attempt cache,
timeouts, a four-MiB response limit, and one configuration-refresh retry. Nothing
logs signed caption URLs. Native caption parsing remains the fallback. Richer
word timing takes priority over later sentence-only responses. Channel exclusions
also prevent the new requests. Background results cannot mute a different video.

**This is not Android listening verification.** The phone was absent from ADB
during this implementation. The last device-tested build remains v0.7. The new
candidate must be installed and checked with CC on/off, seeking, pause/resume,
channel exceptions, and listening around the next clean word before publication.
YouTube may return different results for another region, session, or video.
No available captions means no caption-based filtering; this adds no transcription.

## Other patches and approaches

| Candidate | What the reviewed source does | Does it fix our timing problem? |
| --- | --- | --- |
| [Morphe Captions](https://github.com/MorpheApp/morphe-patches/blob/v2.2.0/README.md) | Caption preferences, translation, bilingual/stable display, PiP | No word-timing acquisition or profanity-audio filter is described. Useful infrastructure already used by this patch. |
| [Morphe caption-track capture](https://github.com/MorpheApp/morphe-patches/blob/v2.2.0/patches/src/main/kotlin/app/morphe/patches/youtube/layout/captions/CaptionTrackCapturePatch.kt) | Fetches a track through the native loader without changing display selection | Useful for native CC-hidden loading. It still fetches the sentence-only native resource in our observed case. |
| [Morphe captions after seek](https://github.com/MorpheApp/morphe-patches/blob/v2.2.0/patches/src/main/kotlin/app/morphe/patches/youtube/layout/captions/CaptionsAfterSeekPatch.kt) | Changes caption cue handling after seeks | Addresses display/loading after a seek, not missing word timestamps. |
| [Morphe caption cookies](https://github.com/MorpheApp/morphe-patches/blob/v2.2.0/extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/CaptionCookiesPatch.java) | Adds user-provided cookies and web user-agent handling | An access option, not a precision fix. Requiring externally copied cookies would violate the product workflow. |
| [RVX / anddea Fix transcript](https://github.com/anddea/revanced-patches/commit/7c42e04) | Rewrites `get_transcript` client version to `20.05.46` | Can address transcript precondition failures. Does not add per-word offsets to timedtext or schedule audio muting. This feature is distinct from its optional cookie setting. |
| [NewPipeExtractor WEB request helper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamHelper.java) | Obtains fresh visitor context for internal player requests | Useful protocol reference. Fresh visitor context alone still returned a reload error here; adding the current player timestamp enabled the richer resource. No NewPipe dependency or source was included in the patch. |
| [NewPipe on-device token generator](https://github.com/TeamNewPipe/NewPipe/blob/v0.29.2/app/src/main/java/org/schabi/newpipe/util/potoken/PoTokenWebView.kt) | On-device WebView session/token generation | Not needed for the successful request above. Subtitle-token support is separately [tracked upstream](https://github.com/TeamNewPipe/NewPipeExtractor/issues/1366); this is not a verified drop-in profanity patch. |

No verified drop-in replacement for precise native profanity muting was found
in the reviewed candidates. That is a scoped research finding, not a claim that
no such project exists anywhere. Do not stack unrelated caption patches and
assume they improve audio timing.

## Reproduce the development checks

The APK needs no JSON dependency: Android already provides `org.json`. Host tests
use the SHA-256-pinned Maven `org.json:json:20240303` solely as a test substitute.
The build workflow runs its mocked protocol tests without contacting YouTube.

```text
python morphe-profanity/tests/run_web_caption_tests.py morphe-src --json-jar json-20240303.jar
```

Add `--live` only for an intentional developer network check. It prints counts,
not signed URLs, cookies, or transcript contents. A passing host check cannot
establish Android integration or audible alignment.

Builds now upload an Actions artifact. Replacing the experimental latest asset
requires a deliberate workflow dispatch with `publish_latest` after device
verification, so an untested timing provider cannot replace the tested download.
