# Morphe profanity filter prototype

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
