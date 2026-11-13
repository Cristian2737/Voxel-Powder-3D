# Morphe profanity filter prototype

For the current installation status, reproducible APK build, and device-test
procedure, see [DEVICE_TESTING.md](DEVICE_TESTING.md). Downloading a `.mpp` alone
does not apply it to an installed YouTube application.

This folder is an **experimental custom patch**, developed separately from the Voxel Powder 3D project. It is intentionally stored only on the separate branch **morphe-profanity-filter-prototype** so the original project's main branch stays unchanged.

## Intended behavior

- Mute a short interval in YouTube audio around caption-detected profanity.
- Replace detected words with asterisks in displayed subtitle text.
- Offer separate toggles for common profanity and irreverent expressions, and custom terms.
- Run locally without uploading speech data.

This is not ContentAngel's source, and it does not include the Chrome extension. It uses Morphe caption and audio-volume hooks.

## GitHub Actions build

The workflow at .github/workflows/build-morphe-profanity.yml clones Morphe source, runs the Python installer, builds the custom patch bundle, and uploads a .mpp artifact when compilation succeeds.

Open Actions > Build Morphe profanity filter, choose a successful workflow run and download **morphe-profanity-filter-mpp.zip** from Artifacts. Extract the ZIP: the patch is always named **morphe-profanity-filter.mpp**, with exactly the same filename on each new build. Replace the previous file in Downloads with the new one. If compilation fails, inspect the job logs. It may require GitHub Packages read credentials; never commit credentials or tokens to source.

**IMPORTANT:** The ZIP/archive filename, internal .mpp filename and source paths are stable between releases. Morphe Manager may import a copy of the source: removing/re-adding the old local source can be necessary even if you replace the file in Downloads. If you previously selected the old patch, do not leave both old and new local sources enabled. The revised patch no longer depends on the unstable StableCaptionBlocksPatch, but it still needs a real device test.

## Manual build

1. Clone https://github.com/MorpheApp/morphe-patches into a folder morphe-src.
2. Run: python3 morphe-profanity/apply_to_morphe.py morphe-src
3. Run: cd morphe-src && ./gradlew buildAndroid
4. If successful, the .mpp file is under patches/build/libs, to be used with a compatible Morphe patcher.

Enable Profanity filter under Morphe Settings > Player after patching YouTube. Select a YouTube caption track.

## Limitations

The first-generation pure-Java matcher and anchored installer were exercised locally. The **revised caption parser approach and full Morphe build/device behavior have not yet been verified**.

- Captions must exist and be loaded; no captions means no filtering.
- Auto-generated captions can be late or inaccurate, and some profanity may slip through.
- Uploaded captions may mute a whole sentence instead of one word.
- Casting, alternative audio routing, background playback and new YouTube versions may bypass muting.
- The installer refuses to edit source files if expected upstream anchors change.

New code: GPL-3.0-or-later. Preserve upstream Morphe notices when redistributing derived code.

## If the old patch still fails with StableCaptionBlocksPatch

The newer bundle is labeled **Cristian Profanity Filter**, not **Morphe Patches**.
If the Morphe Manager error identifies the profanity filter's **source** as Morphe Patches,
you are still selecting the old build. Delete/remove that older imported local .mpp
source in Morphe Manager, extract the artifact from a successful workflow run of
the newest branch revision, and add the replacement .mpp under Sources > Local.
There should be exactly one selectable profanity filter patch from Cristian Profanity Filter.

Replacing the .mpp in Downloads may not update a copy already imported into the
manager. Delete and re-add the manager source after replacing the file. This
fixes source/version confusion; compilation and runtime muting remain unverified.

## v0.3 runtime troubleshooting

The **v0.3** prototype improves timestamp/caption-track matching and adds two
optional switches in YouTube's Morphe Settings > Player > Profanity filter.
The **patch display name, workflow artifact name, and installed .mpp filename
are unchanged**, so replacing an existing local bundle remains straightforward.

To test after installing the latest successful build:

1. Turn on **Enable profanity filter**. The setting description should say
   **Prototype v0.3** (otherwise the old patch was installed).
2. Turn on **Test audio muting (first 5 seconds)**, open a new YouTube video,
   and play from 0:00. The first five seconds should be silent. This checks
   the audio-volume hook even if the video has no captions. Turn this test
   switch back **off** afterward.
3. Turn on **Show filter diagnostics**, turn on English captions for a video
   with timed subtitles, and start it again. A brief in-app message should
   indicate how many mute intervals the parser found. **0** means captions
   loaded but no matching words were found in the parsed caption track.
4. If the audio test works and the caption parser finds mute intervals, the
   main filter should mute the matching intervals; word timing can remain
   approximate, especially on auto-generated captions.

The experimental filter does **not** transcribe speech directly. It needs a
caption track, and it cannot guarantee profanity suppression on videos without
usable captions. The test and diagnostic switches are **off by default**.

This is source-only troubleshooting progress: full compilation and on-device
behavior still require a successful GitHub Actions build and a device test.

## v0.4 — closer to ContentAngel 1.5.1

The next successful artifact keeps the filename **morphe-profanity-filter.mpp**.
The in-app filter summary should read **Prototype v0.4**, allowing it to be
identified separately from earlier builds. **Compiled output/device behavior are
not yet verified.**

The Java implementation now understands YouTube's always-muted `[ __ ]` marker,
uses default word families closer to ContentAngel's, joins adjacent captions
for multi-word phrases, estimates missing word timing and offers controls for
regex, word boundaries, mute lead/fallback duration, censor style and per-channel
Always/Never lists. The new per-video timeline cache prevents a background
caption request from replacing the video playing on screen.

It also runs JVM tests for word detection and timing before the Gradle build.
The test requires no Android dependencies, but it does not prove the native
YouTube player will behave correctly on all videos.

For the **exact feature parity status and Android-only limitations**, see
[CONTENTANGEL_PARITY.md](CONTENTANGEL_PARITY.md).

### How to test

Enable the filter and **Show filter diagnostics**; leave the five-second
**Test audio muting** switch off for normal use. Play a video that contains
actual English profanity and enable its English caption track. The new toast
reports **matches / merged mute windows / captions / segments**, not just the
window count. Matches of zero indicate the caption text did not match; matches
with nonzero windows but no audio suppression suggest a timing or playback-hook
problem. For a quick comparison, the audio self-test still mutes the first five
seconds of a video independently of captions.
