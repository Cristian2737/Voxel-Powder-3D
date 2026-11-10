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

Open Actions > Build Morphe profanity filter, choose a successful workflow run and download **morphe-profanity-filter-mpp** from Artifacts. If compilation fails, inspect the job logs. It may require GitHub Packages read credentials; never commit credentials or tokens to source.

## Manual build

1. Clone https://github.com/MorpheApp/morphe-patches into a folder morphe-src.
2. Run: python3 morphe-profanity/apply_to_morphe.py morphe-src
3. Run: cd morphe-src && ./gradlew buildAndroid
4. If successful, the .mpp file is under patches/build/libs, to be used with a compatible Morphe patcher.

Enable Profanity filter under Morphe Settings > Player after patching YouTube. Select a YouTube caption track.

## Limitations

The pure-Java word matcher and anchored installation script were tested locally; the full Morphe build and device behavior have not yet been verified.

- Captions must exist and be loaded; no captions means no filtering.
- Auto-generated captions can be late or inaccurate, and some profanity may slip through.
- Uploaded captions may mute a whole sentence instead of one word.
- Casting, alternative audio routing, background playback and new YouTube versions may bypass muting.
- The installer refuses to edit source files if expected upstream anchors change.

New code: GPL-3.0-or-later. Preserve upstream Morphe notices when redistributing derived code.
