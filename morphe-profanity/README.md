# Morphe YouTube profanity filter - v0.8 candidate

An independent Android patch that mutes caption-detected profanity and optionally
masks the matching caption text. It runs locally inside patched YouTube using
Morphe's caption parser and AudioTrack hooks. It does not transcribe speech or
upload audio.

This prototype lives on the **morphe-profanity-filter-prototype** branch of this
repository; it is separate from the Voxel Powder 3D project on the main branch.
It is not an official Morphe release or ContentAngel's source code.

## Current verification

The v0.8 candidate adds **Fetch word timing on this device**. It requests the
web caption resource directly from Android using a fresh visitor context and
the current YouTube player signature timestamp, without displaying CC. No
computer, desktop export, browser-cookie transfer, or external service is needed
by this implementation. Word-timed schedules survive later sentence-only loads.

The actual Java fetcher retrieved **3,263 word offsets** on the ibz test video in
a live host check. It passes 47 acquisition contracts and 34 runtime lifecycle
checks, alongside the 35 rule/timing and 41 ContentAngel reference cases.
The phone reconnected, and the same Java provider retrieved those **3,263 word
offsets in 3.4 seconds on Android 14**. Its first three windows match the working
browser reference exactly. [The Android fetch record](tests/android-fetch-verification-v08.json)
describes this silent test; the temporary diagnostic APK was uninstalled.
The signed v0.8 APK was then installed and its hash verified. The live player
loaded the richer schedule, muted the first window, and restored audio. The user
confirmed: **clean mute, following words audible**. [The playback record](tests/device-verification-v08.json)
covers that one passage; broader seek/pause/channel/CC-hidden live checks remain.
The experimental latest download remains v0.7 while broader v0.8 device checks are pending. A source build creates an Actions
artifact; publication is held until a deliberate post-verification dispatch.
See [the request evidence and other-patch comparison](CAPTION_PATCH_RESEARCH.md).

The following records describe the previously tested builds, not v0.8:

Version 0.7 adds **Short mutes for sentence-only captions**, enabled by default.
It estimates the matched characters within the caption line without the broad
sentence margin. Turn it off for broader coverage. Restart after changing it.
This is estimated alignment, not newly measured speech timing: it can miss words
when a caption line does not align with the audio. Real word-offset timing keeps
the v0.6 rules. Unicode-space censor tokens are now consistent on desktop/Android.

The live v0.6 check found the ibz test track supplies 284 whole-line captions and
**zero word-offset segments**. Rich JSON3/SRV3 requests of that same native resource also returned line-only
data. The v0.8 candidate requests a distinct WEB resource, which does have word offsets. Therefore the ContentAngel word-timing reference cases do not establish
precise timing on that video. Version 0.7 passes 35 rule/timing, 41 reference and
25 runtime checks. The corrected build succeeded in
[Actions run 43451439760](https://github.com/Cristian2737/Voxel-Powder-3D/actions/runs/43451439760)
and its installed APK hash matches the desktop output. **All 15 native checks
passed**: three short windows and restoration, pause/resume, seeking, the channel
exception, and actual Android mixer muting/restoration (502 player samples and
319 mixer samples). The first three mutes last 450, 493 and 419 milliseconds.
[The v0.7 test record](tests/device-verification-2026-11-13-v07.json) records the
source and hashes. This verifies the schedule and audio control; a listening
check of estimated spoken-word alignment is still pending. Temporary phone-side
diagnostics were removed and the signed test probe was uninstalled.

Historical v0.6 build verification follows:

Version 0.6 corrects timing rules identified by inspecting the installed
ContentAngel 1.5.1 parser/planner: next-line bounds for rolling captions,
unexpanded single-word fallbacks, and character timing within bounded segments.
It passes **41 synthetic timing cases measured from that implementation**,
**27 matching/timing checks**, and **25 audio lifecycle checks**. The reference
fixtures contain synthetic captions and expected times, not extension source.
Version 0.6 compiled successfully in [GitHub Actions](https://github.com/Cristian2737/Voxel-Powder-3D/actions/runs/43425605334), and a signed APK was built and verified locally.
The [v0.6 build record](tests/build-verification-v06.json) identifies its hashes.
It still needs a live phone check; the results below describe v0.5.

Version 0.5 was built and installed on an Android 14 phone. Live testing of
[ibz's Halloween Americans](https://www.youtube.com/watch?v=8_vTxsB3IYA) loaded
English automatic captions and detected **20 matches / 18 mute windows**. Three
mute intervals, volume restoration, pause/resume, and a channel exception passed
native checks. Android's mixer confirmed actual audio muting, not only an
internal flag. The first-five-seconds diagnostic also worked without displaying
the player controls. The artificial audio test was off for the normal tests.

The previously tested v0.5 build ran 18 matching/timing and 23 host runtime checks. The
[device test record](tests/device-verification-2026-11-13.json) identifies the
exact tested source, bundle and APK. This covers one phone and one video, not
all playback routes or perfect alignment of every spoken word.

## Get the compiled patch bundle

Download [morphe-profanity-filter.mpp](https://github.com/Cristian2737/Voxel-Powder-3D/releases/download/morphe-profanity-latest/morphe-profanity-filter.mpp)
from the experimental latest release. A successful GitHub Actions run also
provides **morphe-profanity-filter-mpp.zip**; extract the `.mpp` inside it. These
filenames stay the same between revisions for existing download automations.

**Downloading an `.mpp` does not modify an installed YouTube app.** Import the
bundle in Morphe Manager's local sources, select **Profanity filter**, patch a
compatible original YouTube APK, and install the resulting APK. If Manager copied
an older local source, remove/re-add that custom source after replacing its file.
Select the custom source named **Cristian Profanity Filter**, without duplicate
older custom filter sources.

For the tested installation path, use the desktop build script described in
[DEVICE_TESTING.md](DEVICE_TESTING.md). It verifies the original Google APK and
builds a separately installed **YouTube Censor Test** app, preserving regular
YouTube. Retain its signing key to install later updates.

## Use it

Open YouTube Settings > Morphe Settings > Player > Profanity filter. The summary
should identify **Prototype v0.7** after installing the updated APK. Enable the filter and select a real caption
track in the player, such as English (auto-generated). The filter is enabled by
default in this custom prototype.

Settings include common profanity, optional irreverent expressions, custom words
and phrases, exclusions prefixed with `-`, advanced regex, word boundaries,
caption masking styles, mute lead/fallback duration, and Always/Never channel
lists. Channel lists accept exact channel IDs or names; Never takes priority.
Restart after editing settings so cached timelines are rebuilt consistently.

For troubleshooting, **Show filter diagnostics** reports matches, merged windows,
caption paragraphs and segments. **Test audio muting (first 5 seconds)** checks
the audio hook independently of captions; leave it off during normal use.

## Build the bundle from source

Start from a fresh checkout with Java 21, an Android SDK, and the upstream
Morphe build dependencies/required GitHub Packages access configured:

```sh
git clone --depth 1 --branch v2.2.0 https://github.com/MorpheApp/morphe-patches.git morphe-src
python3 morphe-profanity/apply_to_morphe.py morphe-src
python3 morphe-profanity/tests/run_runtime_tests.py morphe-src
cd morphe-src
./gradlew buildAndroid --no-daemon
```

The installer validates upstream anchors and refuses to overwrite changed source
files. Use a fresh checkout when upgrading the prototype. A compiled bundle is
produced under `patches/build/libs/`. GitHub Actions performs the same source
installation and host checks and publishes the bundle only after a successful
build. Never commit credentials, signing keys or Google APK files.

## Practical limits

- A caption track must actually load. Captionless videos or profanity missing
  from a transcript cannot be filtered by this implementation.
- Automatic captions can be inaccurate. Without word offsets, timing is
  estimated and can mute several seconds of surrounding speech. The adjustable
  fallback is not a guarantee of exact word boundaries.
- Independently fetching captions while CC is off, casting, live streams,
  picture-in-picture, every playback speed and other YouTube versions still need
  additional work or targeted validation.
- Opaque blocks are a caption redaction style, not a native blur effect.

See [CONTENTANGEL_PARITY.md](CONTENTANGEL_PARITY.md) for the remaining differences
from the browser extension and [DEVICE_TESTING.md](DEVICE_TESTING.md) for detailed
build and phone checks.

New code: GPL-3.0-or-later. Preserve the upstream Morphe notices when
redistributing derived code.
