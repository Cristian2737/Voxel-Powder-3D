/* GPL-3.0-or-later: experimental caption-based profanity filter. */
package app.morphe.patches.youtube.video.profanity

import app.morphe.patcher.patch.patch
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference
import app.morphe.patches.youtube.layout.captions.translatedCaptionPensPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.information.videoTimeHook
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.volume.playerVolumeHookPatch

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/ProfanityFilterPatch;"

@Suppress("unused")
val profanityFilterPatch = patch(
    name = "Profanity filter",
    description = "Mutes caption-timed profanity and optionally masks subtitle text locally. Requires a caption track.",
) {
    dependsOn(
        translatedCaptionPensPatch,
        videoInformationPatch,
        playerVolumeHookPatch,
        settingsPatch,
    )
    compatibleWith(COMPATIBILITY_YOUTUBE)
    execute {
        PreferenceScreen.PLAYER.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_profanity_screen",
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_profanity_filter", summary = true),
                    SwitchPreference("morphe_profanity_censor_captions", summary = true),
                    SwitchPreference("morphe_profanity_default_words", summary = true),
                    SwitchPreference("morphe_profanity_reverent_phrases", summary = true),
                    TextPreference("morphe_profanity_custom_words"),
                    TextPreference("morphe_profanity_custom_regex"),
                    SwitchPreference("morphe_profanity_word_boundary", summary = true),
                    TextPreference("morphe_profanity_mute_buffer"),
                    TextPreference("morphe_profanity_mute_duration"),
                    ListPreference("morphe_profanity_censor_style"),
                    TextPreference("morphe_profanity_always_channels"),
                    TextPreference("morphe_profanity_never_channels"),
                    SwitchPreference("morphe_profanity_diagnostics", summary = true),
                    SwitchPreference("morphe_profanity_test_audio", summary = true),
                ),
            ),
        )
        hookVideoId("$EXTENSION_CLASS->onVideoId(Ljava/lang/String;)V")
        videoTimeHook(EXTENSION_CLASS, "videoTimeChanged")
    }
}