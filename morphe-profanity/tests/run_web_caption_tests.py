#!/usr/bin/env python3
"""Test the real acquisition/parser using org.json as Android's host substitute.

No dependency is added to the APK. Pass --json-jar (org.json 20240303) and
optionally --live to check the real public YouTube request from this host.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("--json-jar", type=Path, required=True)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--live", action="store_true")
    args = parser.parse_args()
    expected = "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"
    if hashlib.sha256(args.json_jar.read_bytes()).hexdigest() != expected:
        raise RuntimeError("Unexpected host-test JSON dependency")
    executable = lambda name: str(args.java_home / "bin" / (name + (".exe" if os.name == "nt" else ""))) if args.java_home else name
    src = HERE.parent / "src"
    with tempfile.TemporaryDirectory(prefix="morphe-web-test-") as temporary:
        root = Path(temporary)
        logger = root / "app/morphe/extension/shared/Logger.java"
        logger.parent.mkdir(parents=True)
        logger.write_text("package app.morphe.extension.shared; import java.util.function.Supplier; "
                          "public class Logger { public static void printInfo(Supplier<String> s, Throwable e) {} }", encoding="utf-8")
        caption_xml = args.upstream / "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/CaptionXml.java"
        subprocess.run([executable("javac"), "-encoding", "UTF-8", "-cp", str(args.json_jar),
                        "-d", str(root), str(src / "ProfanityWebCaptions.java"), str(src / "ProfanityRules.java"),
                        str(src / "ProfanitySchedule.java"), str(caption_xml), str(logger),
                        str(HERE / "ProfanityWebCaptionsTest.java")], check=True)
        command = [executable("java"), "-cp", str(root) + os.pathsep + str(args.json_jar),
                   "app.morphe.extension.youtube.patches.ProfanityWebCaptionsTest"]
        if args.live:
            command.append("--live")
        subprocess.run(command, check=True, timeout=100)


if __name__ == "__main__":
    main()
