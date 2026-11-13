## v0.8 candidate: Android-only richer-caption request

The Java provider now obtains the WEB caption resource on the device, using a
fresh visitor and current player timestamp. Its live host test returned 3,263
word offsets / 3,810 XML segments and 20 match windows. It requires no desktop
export, external service, or WebView. Rich schedules have priority over later
sentence-only native responses. CC-hidden acquisition, stale-result rejection,
request bounds and channel exclusions are host-tested. A silent Android 14 probe also retrieved
3,263 offsets in 3.4 seconds; the first three windows match the browser reference.
The installed full APK also loaded that schedule and muted/restored the first
window; the user confirmed the profanity was cleanly muted and the following
words remained audible. Broader live playback checks remain pending. The v0.7 records below are historical.
See [the existing-patch review](CAPTION_PATCH_RESEARCH.md) for alternatives.

# ContentAngel 1.5.1 behavior reference and parity tracking

Reference: [ContentAngel – Profanity Filter & Caption Muter for YouTube](https://chromewebstore.google.com/detail/celembfncacondbocejjjhjkamnhinoc), version 1.5.1 (Nov 4, 2026).

This is an **independent implementation** for Morphe Android, not copied extension source. The Chrome extension runs in a browser and can read YouTube's web-page player; the native Android player needs different hooks. **Functional parity is the goal, not a current claim.**

## Current native timing finding

The captured native English ASR track for the ibz test video provides whole
sentences and no word offsets. The available rich-format requests retain that
limitation. Version 0.7 offers shorter character-position estimates for these
lines, with broader coverage available by switching them off. This is an
intentional fallback choice and not a claim of ContentAngel word-timing parity.
On this same native input, ContentAngel's pure planner produced a 3084ms first
placeholder window; v0.6 produced 2892ms and the v0.7 short estimate 450ms.
That comparison is not browser playback evidence or proof of spoken alignment.
The corrected v0.7 APK passed the 15 native checks in its device record.

## Reference check and development priorities (November 13, 2026)

The installed ContentAngel 1.5.1 package and its shipped source maps were
inspected directly. Its parser and planner were exercised privately on synthetic
caption inputs, without starting the extension. Version 0.6 matches 41 resulting
word-timing cases within one millisecond. Only synthetic inputs and expected
outputs are kept here; the extension's code is not redistributed.

The checks exposed concrete differences in v0.5: a missing final-word timestamp
was padded past its fallback end; rolling caption display duration could cover
the next line; bounded multi-word segments were widened as though no next
timestamp existed; and partial matches occupied an entire timed segment.
Version 0.6 corrects these rules. This does not yet establish that the Android
track supplies the same word offsets as the browser track: raw native timing
data and a live test of the new APK are still needed.

ContentAngel remains the behavior reference for matching, timing, caption
masking, channel exceptions, and settings. In particular, its stated use of
automatic-caption word times **even when uploaded captions are displayed** is
the key timing behavior to pursue next. Broad sentence-level muting is not the
target behavior when better timing is available.

Priorities for the next implementation pass:

1. Obtain native automatic-caption word offsets and align them with the chosen
   displayed caption track, preserving timestamps through parsing.
2. Validate silence boundaries against those offsets, including the next clean
   word, rather than accepting a nonzero match count as sufficient proof.
3. Load captions for filtering without requiring the viewer to display CC,
   while preserving the viewer's chosen subtitle language and visibility.
4. Keep matching `[ __ ]`, custom literal/regex phrases, cross-caption phrases,
   and Always/Never channel behavior consistent with the reference.

Version 0.5 passed the documented native checks for three mute intervals,
restoration, pause/resume and a channel exception on one Android phone/video.
Those checks establish working native audio control; they do not establish
word-level timing parity. See [the device test record](tests/device-verification-2026-11-13.json).

## Implemented in source (broader device reliability still needs validation)

| ContentAngel behavior | Morphe custom patch |
| --- | --- |
| Detect words via timed captions, not on-device speech recognition | Native timedtext XML parser plus cross-paragraph match planner |
| Mute video audio around a detected phrase and restore afterwards | Existing Morphe AudioTrack volume hook with separate profanity mute channel |
| Recognize YouTube's always-censored '[ __ ]' token | Always-muted pattern, independent of editable word list |
| Catch phrases split between nearby caption lines | Adjacent paragraph text is combined, with a 3-second maximum phrase gap |
| Accurate word intervals when available, broader fallback for uploaded lines | Word offsets within caption segments; estimates used for plain lines |
| Configure literal phrases and modify default entries | Custom comma-separated literal list, -prefix to exclude a default |
| Regex support | Separate advanced regex setting (invalid patterns ignored) |
| Auto word boundaries | Enabled by default for ordinary words |
| Faith-friendly defaults | Common profanity and reverent expressions included, both configurable |
| Adjustable mute buffer and fallback duration | Text settings in milliseconds, default 150 / 1500 |
| Four visual caption styles | Asterisks, [censored], hide, opaque blocks |
| Per-channel Always and Never Censor | Channel-ID or exact-name lists; Never has priority |
| No external analytics and no rewriting a source video | All detection and masking locally within patched YouTube |
| Detect problems separately in audio and caption parsing | Five-second AudioTrack self-test and caption match/window toast |

## Still missing or different

- **Automatic transcript fetching with captions disabled.** ContentAngel can cause the web player to fetch captions as needed. The prototype currently depends on YouTube's native caption parser actually loading a track.
- **Automatic-word timing fetch and line alignment even when uploaded captions are selected.** If multiple tracks happen to be loaded, the prototype prefers auto timing; it does not yet independently fetch or align that track.
- **True blurred caption text.** Native Android's renderer needs a separate visual hook for blur; the current blocks style is opaque redaction, not blur.
- **Convenient current-channel buttons and Chrome popup interface.** Native editable channel-ID/name lists work, but the browser's user experience is not reproduced.
- **Complete behavior parity for Shorts, videos without captions, captions in other formats, picture-in-picture, live streams, seek/casting, and fast playback.** These still need targeted work and phone testing.
- **No guarantee of 100% filtering.** Caption files may omit or mis-transcribe profanity, and audio and caption timing can diverge.

## Testing

GitHub Actions runs pure-JVM matching/timing regression checks before attempting to compile the Morphe patch bundle.

For device tests: turn on filter and diagnostics, turn off the first-five-seconds audio self-test, enable English captions on a video with known profanity, and read the diagnostic toast. If it says '0 matches', caption data or matching is missing. If it reports matches and windows but no muting, check time alignment and whether another caption source replaced it.

Keep the output **morphe-profanity-filter.mpp** and ZIP **morphe-profanity-filter-mpp.zip** filenames unchanged across builds.
