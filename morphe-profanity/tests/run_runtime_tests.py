#!/usr/bin/env python3
"""Exercise real filter/volume Java against small Android host-test doubles.

Pass an upstream checkout AFTER apply_to_morphe.py. This tests lifecycle and
volume composition; it cannot establish that native YouTube hooks are installed.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
STUBS = {
    "android/os/Looper.java": """package android.os;
public class Looper { public static Looper getMainLooper() { return new Looper(); } }
""",
    "android/os/Handler.java": """package android.os;
public class Handler { public Handler(Looper l) {} public boolean postDelayed(Runnable r, long ms) { return true; } }
""",
    "android/media/AudioTrack.java": """package android.media;
public class AudioTrack {
    public static final int PLAYSTATE_PLAYING=3;
    public int state=3; public float volume=1;
    public int getPlayState() { return state; }
    public int setVolume(float v) { volume=v; return 0; }
}
""",
    "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
    "app/morphe/extension/shared/Logger.java": """package app.morphe.extension.shared;
import java.util.function.Supplier;
public class Logger {
    public static void printException(Supplier<String> s, Throwable e) { throw new AssertionError(s.get(), e); }
    public static void printDebug(Supplier<String> s, Throwable e) { throw new AssertionError(s.get(), e); }
}
""",
    "app/morphe/extension/shared/Utils.java": """package app.morphe.extension.shared;
public class Utils {
    public static void runOnMainThreadNowOrLater(Runnable r) { r.run(); }
    public static void runOnMainThreadDelayed(Runnable r, long ms) {}
    public static void showToastShort(String text) {}
}
""",
    "app/morphe/extension/youtube/settings/Settings.java": """package app.morphe.extension.youtube.settings;
public class Settings {
    public static class Value<T> {
        T value; Value(T initial) { value=initial; }
        public T get() { return value; } public void save(T next) { value=next; }
    }
    public static final Value<Boolean> PROFANITY_FILTER=new Value<>(true),
        PROFANITY_CENSOR_CAPTIONS=new Value<>(true), PROFANITY_DEFAULT_WORDS=new Value<>(true),
        PROFANITY_REVERENT_PHRASES=new Value<>(false), PROFANITY_WORD_BOUNDARY=new Value<>(true),
        PROFANITY_DIAGNOSTICS=new Value<>(false), PROFANITY_TEST_AUDIO=new Value<>(false);
    public static final Value<String> PROFANITY_CUSTOM_WORDS=new Value<>(""),
        PROFANITY_CUSTOM_REGEX=new Value<>(""), PROFANITY_MUTE_BUFFER=new Value<>("150"),
        PROFANITY_MUTE_DURATION=new Value<>("1500"), PROFANITY_CENSOR_STYLE=new Value<>("asterisks"),
        PROFANITY_ALWAYS_CHANNELS=new Value<>(""), PROFANITY_NEVER_CHANNELS=new Value<>("");
}
""",
    "app/morphe/extension/youtube/patches/VideoInformation.java": """package app.morphe.extension.youtube.patches;
public class VideoInformation {
    static String video="abcdefghijk"; static long time;
    public static String getVideoId() { return video; }
    public static long getVideoTime() { return time; }
    public static String getChannelId() { return "channel-1"; }
    public static String getChannelName() { return "ibz"; }
}
""",
    "app/morphe/extension/youtube/patches/AutoTranslateCaptionsPatch.java": """package app.morphe.extension.youtube.patches;
import java.net.URI; import java.util.*;
public class AutoTranslateCaptionsPatch {
    public interface CaptionTrackInterface {
        String patch_getUrl(); String patch_getVssId(); String patch_getLanguageCode();
    }
    public static Map<String,String> captionParameters(String url) {
        Map<String,String> params=new HashMap<>();
        for (String part : URI.create(url).getQuery().split("&")) {
            String[] kv=part.split("=",2); if(kv.length==2) params.put(kv[0],kv[1]);
        }
        return params;
    }
}
""",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    java_root = args.upstream / "extensions/youtube/src/main/java/app/morphe/extension/youtube/patches"
    sources = [java_root / name for name in ("ProfanityRules.java", "ProfanitySchedule.java",
                                            "ProfanityFilterPatch.java", "PlayerVolumePatch.java")]
    sources.append(HERE / "ProfanityRuntimeTest.java")
    executable = lambda name: str(args.java_home / "bin" / (name + (".exe" if os.name == "nt" else ""))) if args.java_home else name
    with tempfile.TemporaryDirectory(prefix="morphe-runtime-test-") as temporary:
        root = Path(temporary)
        for path, content in STUBS.items():
            stub = root / path
            stub.parent.mkdir(parents=True, exist_ok=True)
            stub.write_text(content, encoding="utf-8")
            sources.append(stub)
        classes = root / "classes"
        classes.mkdir()
        subprocess.run([executable("javac"), "-encoding", "UTF-8", "-d", str(classes), *map(str, sources)], check=True)
        subprocess.run([executable("java"), "-cp", str(classes),
                        "app.morphe.extension.youtube.patches.ProfanityRuntimeTest"], check=True)


if __name__ == "__main__":
    main()
