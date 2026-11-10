#!/usr/bin/env python3
"""Apply a caption-based profanity filter to a MorpheApp/morphe-patches checkout.

This installer intentionally does not depend on StableCaptionBlocksPatch.
Usage: python3 morphe-profanity/apply_to_morphe.py morphe-src
"""
from pathlib import Path
import argparse
import shutil
import sys

HERE = Path(__file__).resolve().parent


def inject(file: Path, before: str, after: str) -> None:
    original = file.read_text(encoding="utf-8")
    if after in original:
        print("Already applied:", file)
        return
    matches = original.count(before)
    if matches != 1:
        raise RuntimeError(
            f"Upstream source changed: expected one matching anchor, found {matches} in {file}\n"
            f"Anchor: {before[:100]!r}"
        )
    file.write_text(original.replace(before, after), encoding="utf-8")
    print("Modified:", file)


def install(root: Path) -> None:
    if not (root / "patches/src/main/kotlin/app/morphe/patches/youtube").is_dir():
        raise RuntimeError("Expected MorpheApp/morphe-patches checkout")
    java = root / "extensions/youtube/src/main/java/app/morphe/extension/youtube"
    kotlin = root / "patches/src/main/kotlin/app/morphe/patches/youtube"

    for name in ("ProfanityRules.java", "ProfanitySchedule.java", "ProfanityFilterPatch.java"):
        source = HERE / "src" / name
        target = java / "patches" / name
        if target.is_file() and target.read_bytes() != source.read_bytes():
            raise RuntimeError(f"Refusing to overwrite modified file: {target}")
        shutil.copyfile(source, target)
        print("Installed:", target)

    patch_file = kotlin / "video/profanity/ProfanityFilterPatch.kt"
    patch_file.parent.mkdir(parents=True, exist_ok=True)
    contents = (HERE / "src/ProfanityFilterPatch.kt").read_text(encoding="utf-8")
    if patch_file.is_file() and patch_file.read_text(encoding="utf-8") != contents:
        raise RuntimeError(f"Refusing to overwrite modified file: {patch_file}")
    patch_file.write_text(contents, encoding="utf-8")
    print("Installed:", patch_file)

    inject(
        java / "settings/Settings.java",
        '    public static final BooleanSetting STABLE_CAPTION_BLOCKS = new BooleanSetting("morphe_stable_caption_blocks", FALSE, true,',
        '''    // Opt-in, locally processed subtitle-timed profanity muting and caption masking.
    public static final BooleanSetting PROFANITY_FILTER = new BooleanSetting("morphe_profanity_filter", TRUE, true);
    public static final BooleanSetting PROFANITY_CENSOR_CAPTIONS = new BooleanSetting("morphe_profanity_censor_captions", TRUE, parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_DEFAULT_WORDS = new BooleanSetting("morphe_profanity_default_words", TRUE, parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_REVERENT_PHRASES = new BooleanSetting("morphe_profanity_reverent_phrases", TRUE, parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_CUSTOM_WORDS = new StringSetting("morphe_profanity_custom_words", "", parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_CUSTOM_REGEX = new StringSetting("morphe_profanity_custom_regex", "", parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_WORD_BOUNDARY = new BooleanSetting("morphe_profanity_word_boundary", TRUE, parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_MUTE_BUFFER = new StringSetting("morphe_profanity_mute_buffer", "150", parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_MUTE_DURATION = new StringSetting("morphe_profanity_mute_duration", "1500", parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_CENSOR_STYLE = new StringSetting("morphe_profanity_censor_style", "asterisks", parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_ALWAYS_CHANNELS = new StringSetting("morphe_profanity_always_channels", "", parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_NEVER_CHANNELS = new StringSetting("morphe_profanity_never_channels", "", parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_DIAGNOSTICS = new BooleanSetting("morphe_profanity_diagnostics", FALSE, parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_TEST_AUDIO = new BooleanSetting("morphe_profanity_test_audio", FALSE, parent(PROFANITY_FILTER));
    public static final BooleanSetting STABLE_CAPTION_BLOCKS = new BooleanSetting("morphe_stable_caption_blocks", FALSE, true,''')

    # This existing patched parser is installed without requiring the native
    # StableCaptionBlocksPatch that failed in the previous device report.
    translated = java / "patches/TranslatedCaptionPensPatch.java"
    inject(
        translated,
        '''        CaptionTracks.setTrack(parsedTrack);
        StableCaptionBlocks.setAutomaticTrack(automatic);''',
        '''        CaptionTracks.setTrack(parsedTrack);
        StableCaptionBlocks.setAutomaticTrack(automatic);
        ProfanityFilterPatch.setParsedTrack(parsedTrack);''')
    inject(
        translated,
        '''if (input == null || (!repair && !StableCaptionBlocks.isCapturing() && !CaptionTracks.observing())) return input;''',
        '''if (input == null || (!repair && !StableCaptionBlocks.isCapturing()
                && !CaptionTracks.observing() && !ProfanityFilterPatch.isEnabled())) return input;''')
    inject(
        translated,
        '''        StableCaptionBlocks.capture(document);
        byte[] repaired = repair''',
        '''        StableCaptionBlocks.capture(document);
        ProfanityFilterPatch.captureCaptionDocument(document);
        byte[] repaired = repair''')
    inject(
        translated,
        '''        if (repaired == copy.bytes) return copy.input;''',
        '''        if (document != null) repaired = ProfanityFilterPatch.censorCaptionBytes(repaired);
        if (repaired == copy.bytes) return copy.input;''')

    # Identify which original or auto-caption track was selected.
    # In particular, turning subtitles off clears the mute schedule.
    selection = java / "patches/PreferredCaptionLanguagePatch.java"
    inject(
        selection,
        '''        CaptionTrackInterface selected = choosePreferredCaptionTrack(subtitleManager, originalTrack);
        rememberSelection(subtitleManager, selected);''',
        '''        CaptionTrackInterface selected = choosePreferredCaptionTrack(subtitleManager, originalTrack);
        ProfanityFilterPatch.onCaptionSelected(selected);
        rememberSelection(subtitleManager, selected);''')
    inject(
        selection,
        '''            rememberSelection(subtitleManager, track);
            if (CaptionTracks.dualCaptionEnabled()''',
        '''            ProfanityFilterPatch.onCaptionSelected(track);
            rememberSelection(subtitleManager, track);
            if (CaptionTracks.dualCaptionEnabled()''')

    # Use a separate channel from user-mute and TTS ducking.
    volume = java / "patches/PlayerVolumePatch.java"
    inject(
        volume,
        '''    private static volatile boolean muted;
    private static volatile float lastBaseVolume''',
        '''    private static volatile boolean muted;
    private static volatile boolean profanityMuted;
    private static volatile float lastBaseVolume''')
    inject(
        volume,
        '''        return muted ? 0f : duckMultiplier;''',
        '''        return (muted || profanityMuted) ? 0f : duckMultiplier;''')
    inject(
        volume,
        '''    public static boolean isMuted() {
        return muted;
    }''',
        '''    public static boolean isMuted() {
        return muted;
    }

    /** Muting for detected words, independent of the user mute button. */
    public static void setProfanityMuted(boolean value) {
        if (profanityMuted == value) return;
        profanityMuted = value;
        applyMultiplier();
    }''')

    inject(
        root / "patches/src/main/resources/addresources/values/youtube/strings.xml",
        "</resources>",
        '''    <!-- Optional, experimental profanity filter -->
    <string name="morphe_profanity_screen_title">Profanity filter</string>
    <string name="morphe_profanity_screen_summary">Mute profanity detected in timed subtitles</string>
    <string name="morphe_profanity_filter_title">Enable profanity filter</string>
    <string name="morphe_profanity_filter_summary">Prototype v0.4: ContentAngel-inspired caption timing; restart after changing settings.</string>
    <string name="morphe_profanity_censor_captions_title">Censor caption text</string>
    <string name="morphe_profanity_censor_captions_summary">Replace matching words with asterisks</string>
    <string name="morphe_profanity_default_words_title">Filter common profanity</string>
    <string name="morphe_profanity_default_words_summary">Use the built-in common-word list</string>
    <string name="morphe_profanity_reverent_phrases_title">Filter irreverent expressions</string>
    <string name="morphe_profanity_reverent_phrases_summary">Include oh my God and Jesus Christ</string>
    <string name="morphe_profanity_custom_words_title">Custom words / exclusions</string>
    <string name="morphe_profanity_custom_words_summary">Literal words and phrases separated by commas; prefix - to exclude a default</string>
    <string name="morphe_profanity_custom_regex_title">Custom regex patterns</string>
    <string name="morphe_profanity_custom_regex_summary">Advanced: each expression on a separate line; invalid entries ignored</string>
    <string name="morphe_profanity_word_boundary_title">Match word boundaries</string>
    <string name="morphe_profanity_word_boundary_summary">Avoid partial-word matches for normal words</string>
    <string name="morphe_profanity_mute_buffer_title">Mute before word (milliseconds)</string>
    <string name="morphe_profanity_mute_buffer_summary">0–500; defaults to 150</string>
    <string name="morphe_profanity_mute_duration_title">Fallback mute duration (milliseconds)</string>
    <string name="morphe_profanity_mute_duration_summary">100–5000; defaults to 1500 when precise word timing is unavailable</string>
    <string name="morphe_profanity_censor_style_title">Caption censor style</string>
    <string name="morphe_profanity_censor_style_summary">Asterisks, [censored], hide or blocks</string>
    <string name="morphe_profanity_always_channels_title">Always censor channels</string>
    <string name="morphe_profanity_always_channels_summary">Exact channel IDs/names separated by commas; override global switch</string>
    <string name="morphe_profanity_never_channels_title">Never censor channels</string>
    <string name="morphe_profanity_never_channels_summary">Exact channel IDs/names; takes priority over Always censor</string>
    <string name="morphe_profanity_diagnostics_title">Show filter diagnostics</string>
    <string name="morphe_profanity_diagnostics_summary">Show the number of detected mute intervals when captions load</string>
    <string name="morphe_profanity_test_audio_title">Test audio muting (first 5 seconds)</string>
    <string name="morphe_profanity_test_audio_summary">Temporarily silence the first five seconds of each video to test audio controls; turn off afterward</string>
</resources>''')

    # The native preference list uses the same four conceptual styles as the
    # browser extension, with opaque blocks instead of unsupported real blur.
    inject(
        root / "patches/src/main/resources/addresources/values/youtube/arrays.xml",
        "</resources>",
        '''    <!-- Morphe profanity filter caption styles -->
    <string-array name="morphe_profanity_censor_style_entries">
        <item>Asterisks</item>
        <item>[censored]</item>
        <item>Hidden words</item>
        <item>Opaque blocks (blur alternative)</item>
    </string-array>
    <string-array name="morphe_profanity_censor_style_entry_values">
        <item>asterisks</item>
        <item>brackets</item>
        <item>hide</item>
        <item>blocks</item>
    </string-array>
</resources>''')

    # Give the custom bundle its own visible source name. This is a Morphe-derived
    # test build, not an official Morphe Patches release.
    inject(
        root / "patches/build.gradle.kts",
        '''        name = "Morphe Patches"
        description = "Patches for Morphe"
        source = "git@github.com:MorpheApp/morphe-patches.git"
        author = "MorpheApp"''',
        '''        name = "Cristian Profanity Filter"
        description = "Experimental caption-timed profanity filter based on Morphe patches"
        source = "https://github.com/Cristian2737/Voxel-Powder-3D/tree/morphe-profanity-filter-prototype"
        author = "Cristian2737"''')

    print("Installed updated caption-parser approach. Run: ./gradlew buildAndroid")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    args = parser.parse_args()
    try:
        install(args.repository.resolve())
    except (OSError, RuntimeError) as error:
        print("FAILED:", error, file=sys.stderr)
        sys.exit(1)
