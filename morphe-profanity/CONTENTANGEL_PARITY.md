# ContentAngel 1.5.1 behavior reference and parity tracking

Reference: [ContentAngel – Profanity Filter & Caption Muter for YouTube](https://chromewebstore.google.com/detail/celembfncacondbocejjjhjkamnhinoc), version 1.5.1 (Nov 4, 2026).

This is an **independent implementation** for Morphe Android, not copied extension source. The Chrome extension runs in a browser and can read YouTube's web-page player; the native Android player needs different hooks. **Functional parity is the goal, not a current claim.**

## Implemented in source (end-to-end device reliability not yet proven)

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
