#!/usr/bin/env python3
"""Apply the caption-based profanity filter to a MorpheApp/morphe-patches checkout.

Usage: python apply_to_morphe.py /path/to/morphe-patches
Safe for repeated runs: anchored edits are idempotent.
"""
from pathlib import Path
import argparse
import shutil
import sys

ROOT = Path(__file__).resolve().parent


def inject(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        print(f"Already applied: {path}")
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one upstream anchor in {path}, found {count}: {old[:90]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"Edited: {path}")


def main(repository: Path) -> None:
    if not (repository / "patches/src/main/kotlin/app/morphe/patches/youtube").is_dir():
        raise RuntimeError("Not a MorpheApp/morphe-patches source checkout")

    ext = repository / "extensions/youtube/src/main/java/app/morphe/extension/youtube"
    patches = repository / "patches/src/main/kotlin/app/morphe/patches/youtube"
    for filename in ("ProfanityRules.java", "ProfanityFilterPatch.java"):
        source = ROOT / "src" / filename
        target = ext / "patches" / filename
        if target.exists() and target.read_bytes() != source.read_bytes():
            raise RuntimeError(f"Refusing to overwrite customized file {target}")
        shutil.copyfile(source, target)
        print(f"Installed: {target}")

    new_patch = patches / "video/profanity/ProfanityFilterPatch.kt"
    new_patch.parent.mkdir(parents=True, exist_ok=True)
    contents = (ROOT / "src/ProfanityFilterPatch.kt").read_text(encoding="utf-8")
    if new_patch.exists() and new_patch.read_text(encoding="utf-8") != contents:
        raise RuntimeError(f"Refusing to overwrite customized file {new_patch}")
    new_patch.write_text(contents, encoding="utf-8")
    print(f"Installed: {new_patch}")

    inject(
        ext / "settings/Settings.java",
        '    public static final BooleanSetting STABLE_CAPTION_BLOCKS = new BooleanSetting("morphe_stable_caption_blocks", FALSE, true,',
        '''    // Opt-in, locally processed caption-based muting and subtitle masking.
    public static final BooleanSetting PROFANITY_FILTER = new BooleanSetting("morphe_profanity_filter", FALSE, true);
    public static final BooleanSetting PROFANITY_CENSOR_CAPTIONS = new BooleanSetting("morphe_profanity_censor_captions", TRUE, parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_DEFAULT_WORDS = new BooleanSetting("morphe_profanity_default_words", TRUE, parent(PROFANITY_FILTER));
    public static final BooleanSetting PROFANITY_REVERENT_PHRASES = new BooleanSetting("morphe_profanity_reverent_phrases", TRUE, parent(PROFANITY_FILTER));
    public static final StringSetting PROFANITY_CUSTOM_WORDS = new StringSetting("morphe_profanity_custom_words", "", parent(PROFANITY_FILTER));
    public static final BooleanSetting STABLE_CAPTION_BLOCKS = new BooleanSetting("morphe_stable_caption_blocks", FALSE, true,''')

    inject(
        ext / "patches/StableCaptionBlocks.java",
        '''            && (Settings.STABLE_CAPTION_BLOCKS.get()
            || AutoTranslateCaptionsPatch.translationLanguage(Settings.SECONDARY_CAPTION_LANGUAGE.get()) != null);''',
        '''            && (Settings.STABLE_CAPTION_BLOCKS.get()
            || AutoTranslateCaptionsPatch.translationLanguage(Settings.SECONDARY_CAPTION_LANGUAGE.get()) != null
            || ProfanityFilterPatch.isEnabled());''')

    inject(
        ext / "patches/StableCaptionBlockPolicy.java",
        '''    private final List<Atom> atoms;''',
        '''    static final class CensorWindow {
        final long start, end;
        CensorWindow(long start, long end) { this.start = start; this.end = end; }
    }

    /**
     * Return merged mute windows from original (non-translated) source atoms.
     * If words have no individual timestamps, mute the larger caption interval.
     */
    List<CensorWindow> censorWindows(java.util.regex.Pattern rules) {
        List<CensorWindow> candidates = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++) {
            StringBuilder phrase = new StringBuilder();
            for (int j = i; j < Math.min(atoms.size(), i + 4); j++) {
                Atom current = atoms.get(j);
                if (j != i && current.barrierBefore) break;
                phrase.append(current.text);
                if (!ProfanityRules.contains(phrase.toString(), rules)) continue;
                long start = Math.max(0, atoms.get(i).start - 150);
                long end = current.end;
                if (j + 1 < atoms.size() && !atoms.get(j + 1).barrierBefore
                        && atoms.get(j + 1).start > current.start) {
                    end = Math.min(end, atoms.get(j + 1).start);
                }
                if (end > atoms.get(i).start) candidates.add(new CensorWindow(start, end + 220));
                break;
            }
        }
        if (candidates.isEmpty()) return List.of();
        candidates.sort((a,b) -> Long.compare(a.start, b.start));
        List<CensorWindow> merged = new ArrayList<>();
        CensorWindow last = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            CensorWindow next = candidates.get(i);
            if (next.start <= last.end) last = new CensorWindow(last.start, Math.max(last.end, next.end));
            else { merged.add(last); last = next; }
        }
        merged.add(last);
        return List.copyOf(merged);
    }

    private final List<Atom> atoms;''')

    inject(
        ext / "patches/CaptionTracks.java",
        '''            selectionDisabled = true;
            selectedInputLanguage = null;''',
        '''            selectionDisabled = true;
            ProfanityFilterPatch.captionsDisabled();
            selectedInputLanguage = null;''')
    inject(
        ext / "patches/CaptionTracks.java",
        '''            if (current) invalidateTranslation();
            return;
        }

        // A native prefetch may later be selected from the native parsed-result cache.''',
        '''            if (current) {
                invalidateTranslation();
                ProfanityFilterPatch.captionsDisabled();
            }
            return;
        }

        if (current) ProfanityFilterPatch.onCaptionSource(track.video, primary);

        // A native prefetch may later be selected from the native parsed-result cache.''')
    inject(
        ext / "patches/CaptionTracks.java",
        '''            if (state == null && dualCaptionEnabled() && !track.automatic && !track.translated''',
        '''            if (state == null && (dualCaptionEnabled() || ProfanityFilterPatch.isEnabled())
                    && !track.automatic && !track.translated''')
    inject(
        ext / "patches/CaptionTracks.java",
        '''                    state.captured = new Captured(track, policy);
                    timeline.patch_setBlockState(state);''',
        '''                    state.captured = new Captured(track, policy);
                    if (ProfanityFilterPatch.isEnabled())
                        ProfanityFilterPatch.onCaptionSource(track.video, policy);
                    timeline.patch_setBlockState(state);''')

    inject(
        ext / "patches/TranslatedCaptionPensPatch.java",
        '''        byte[] repaired = repair
                ? TranslatedRollingCaptionPolicy.repair(copy.bytes, document)
                : copy.bytes;
        if (repaired == copy.bytes) return copy.input;''',
        '''        byte[] repaired = repair
                ? TranslatedRollingCaptionPolicy.repair(copy.bytes, document)
                : copy.bytes;
        // Capture above uses original timing/text; only valid caption XML is modified.
        if (document != null) repaired = ProfanityFilterPatch.censorCaptionBytes(repaired);
        if (repaired == copy.bytes) return copy.input;''')

    inject(
        ext / "patches/PlayerVolumePatch.java",
        '''    private static volatile boolean muted;
    private static volatile float lastBaseVolume''',
        '''    private static volatile boolean muted;
    private static volatile boolean profanityMuted;
    private static volatile float lastBaseVolume''')
    inject(
        ext / "patches/PlayerVolumePatch.java",
        '''        return muted ? 0f : duckMultiplier;''',
        '''        return (muted || profanityMuted) ? 0f : duckMultiplier;''')
    inject(
        ext / "patches/PlayerVolumePatch.java",
        '''    public static boolean isMuted() {
        return muted;
    }''',
        '''    public static boolean isMuted() {
        return muted;
    }

    /** Independent from the user mute button and voiceover ducking. */
    public static void setProfanityMuted(boolean mute) {
        if (mute == profanityMuted) return;
        profanityMuted = mute;
        applyMultiplier();
    }''')

    inject(
        repository / "patches/src/main/resources/addresources/values/youtube/strings.xml",
        "</resources>",
        '''    <!-- Experimental profanity filter -->
    <string name="morphe_profanity_screen_title">Profanity filter</string>
    <string name="morphe_profanity_screen_summary">Automatically mute caption-detected profanity</string>
    <string name="morphe_profanity_filter_title">Enable profanity filter</string>
    <string name="morphe_profanity_filter_summary">Requires captions and an app restart after enabling</string>
    <string name="morphe_profanity_censor_captions_title">Censor caption text</string>
    <string name="morphe_profanity_censor_captions_summary">Replace detected words with asterisks</string>
    <string name="morphe_profanity_default_words_title">Filter common profanity</string>
    <string name="morphe_profanity_default_words_summary">Uses a default list</string>
    <string name="morphe_profanity_reverent_phrases_title">Filter irreverent expressions</string>
    <string name="morphe_profanity_reverent_phrases_summary">Includes oh my God and Jesus Christ</string>
    <string name="morphe_profanity_custom_words_title">Custom words / exclusions</string>
    <string name="morphe_profanity_custom_words_summary">Separate phrases with commas; prefix - to exclude a default</string>
</resources>''')

    print("Source changes applied. Build with ./gradlew buildAndroid.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository", type=Path)
    args = parser.parse_args()
    try:
        main(args.repository.resolve())
    except (RuntimeError, OSError) as error:
        print("FAILED:", error, file=sys.stderr)
        sys.exit(1)
