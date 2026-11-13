#!/usr/bin/env python3
"""Build a separately installed YouTube Censor Test APK from a compiled MPP.

This applies an already compiled bundle; rebuilding the MPP from edited Java or
Kotlin still requires the source build described in README.md.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
PACKAGE = "app.morphe.android.youtube.censor"
DOWNLOADS = {
    "morphe-desktop.jar": (
        "https://github.com/MorpheApp/morphe-desktop/releases/download/v2.1.1/morphe-desktop-2.1.1-all.jar",
        "3a2efefd567b44dde15971a58d5aa649a32aba30781d3400dc08d688c720168f",
    ),
    "youtube-stock.apk": (
        "https://archive.org/download/jhc-apks/apks/com.google.android.youtube/com.google.android.youtube-21.30.216-all.apk",
        "3775a15129dcc4e5b80fc17b7e0841b7fdd1ca89a6e72baa798806f666c74d02",
    ),
}
GOOGLE_CERTIFICATES = {
    "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
    "5aad2bee6db95d17e05a08d7d1e64c10a1511879154483916b6ae6c7fd9cb0c6",
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fetch(path, url, expected):
    if path.is_file() and digest(path) == expected:
        return
    temporary = path.with_suffix(path.suffix + ".download")
    print("Downloading", path.name, flush=True)
    request = urllib.request.Request(url, headers={"User-Agent": "Morphe-profanity-device-test"})
    with urllib.request.urlopen(request, timeout=90) as source, temporary.open("wb") as target:
        shutil.copyfileobj(source, target)
    if digest(temporary) != expected:
        raise RuntimeError("Unexpected download hash: " + str(temporary))
    temporary.replace(path)


def run(arguments, *, env=None, log=None):
    result = subprocess.run([str(a) for a in arguments], env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            encoding="utf-8", errors="replace", timeout=900)
    if log:
        log.write_text(result.stdout, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(result.stdout[-12000:])
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", required=True, type=Path, help="Compiled morphe-profanity-filter.mpp")
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--output", type=Path, default=HERE / "build" / "device-test")
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--serial", help="Required with --install; select one connected device explicitly")
    args = parser.parse_args()
    if not args.bundle.is_file():
        parser.error("The compiled --bundle does not exist")
    if not args.java_home or not args.sdk:
        parser.error("Provide Java 21+ --java-home and Android --sdk")
    if args.install and not args.serial:
        parser.error("--install requires --serial")
    java = args.java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    sdk_versions = sorted((args.sdk / "build-tools").iterdir(), key=lambda p: [int(v) for v in p.name.split(".") if v.isdigit()])
    tools = sdk_versions[-1]
    signer = tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    aapt = tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    environment = dict(os.environ, JAVA_HOME=str(args.java_home))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    for name, (url, expected) in DOWNLOADS.items():
        fetch(output / name, url, expected)
    bundle = output / "morphe-profanity-filter.mpp"
    if args.bundle.resolve() != bundle:
        shutil.copyfile(args.bundle, bundle)
    certs = run([signer, "verify", "--print-certs", output / "youtube-stock.apk"], env=environment)
    if not all(certificate in certs for certificate in GOOGLE_CERTIFICATES):
        raise RuntimeError("The stock APK does not have the expected Google certificates")
    cli = [java, "-Xmx3G", "-jar", output / "morphe-desktop.jar"]
    options_path = output / "options.json"
    run(cli + ["options-create", "-p", bundle, "-f", "com.google.android.youtube", "-o", options_path])
    options = json.loads(options_path.read_text(encoding="utf-8-sig"))
    patches = options[0]["patches"]
    selected = {"Clone app", "GmsCore support", "Profanity filter", "Custom branding", "Hide ads"}
    if not selected.issubset(patches):
        raise RuntimeError("The bundle is missing a required patch, including Profanity filter")
    for name, settings in patches.items():
        settings["enabled"] = name in selected
    patches["Clone app"]["options"]["packageName"] = PACKAGE
    patches["Custom branding"]["options"]["customName"] = "YouTube Censor Test"
    options_path.write_text(json.dumps(options, indent=2), encoding="utf-8")
    apk = output / "youtube-censor-test.apk"
    report_path = output / "patch-result.json"
    print("Applying custom filter and its dependencies...", flush=True)
    run(cli + ["patch", "-p", bundle, "--options-file", options_path, "--striplibs", "arm64-v8a",
               "-o", apk, "-r", report_path, "-t", output / "patch-temp", output / "youtube-stock.apk"],
        log=output / "build.log")
    report = json.loads(report_path.read_text(encoding="utf-8-sig"))
    if not report.get("success") or "Profanity filter" not in report.get("appliedPatches", []):
        raise RuntimeError("The patch result did not confirm the profanity filter")
    badging = run([aapt, "dump", "badging", apk])
    if "name='" + PACKAGE + "'" not in badging:
        raise RuntimeError("Unexpected output package")
    run([signer, "verify", apk], env=environment)
    with zipfile.ZipFile(apk) as archive:
        marker = any(b"ProfanityFilterPatch" in archive.read(name)
                     for name in archive.namelist() if name.startswith("classes") and name.endswith(".dex"))
    if not marker:
        raise RuntimeError("Filter code is absent from the generated APK")
    verification = {"package": PACKAGE, "apk_sha256": digest(apk), "bundle_sha256": digest(bundle),
                    "patch_applied": True, "signature_verified": True,
                    "device_playback_verified": False}
    if args.install:
        adb = args.adb or args.sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        run([adb, "-s", args.serial, "install", "--no-streaming", "-r", apk], env=environment)
        verification["installed"] = True
    (output / "build-verification.json").write_text(json.dumps(verification, indent=2), encoding="utf-8")
    print("Built and verified:", apk)
    print("Live playback still needs the device checks in DEVICE_TESTING.md.")


if __name__ == "__main__":
    main()
