package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.viewer.AudioChannels
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsAudioScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_audio

    @Composable
    override fun getPreferences(): List<Preference> {
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

        val prefLangs = playerPreferences.preferredAudioLanguages()
        val pitchCorrection = playerPreferences.enablePitchCorrection()
        val audioChannels = playerPreferences.audioChannels()
        val boostCapPref = playerPreferences.volumeBoostCap()
        val boostCap by boostCapPref.collectAsState()

        return listOf(
            Preference.PreferenceItem.EditTextInfoPreference(
                pref = prefLangs,
                title = stringResource(MR.strings.pref_player_audio_lang),
                dialogSubtitle = stringResource(MR.strings.pref_player_audio_lang_info),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = pitchCorrection,
                title = stringResource(MR.strings.pref_player_audio_pitch_correction),
                subtitle = stringResource(MR.strings.pref_player_audio_pitch_correction_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = audioChannels,
                title = stringResource(MR.strings.pref_player_audio_channels),
                entries = AudioChannels.entries.associateWith {
                    stringResource(it.textRes)
                }.toImmutableMap(),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = boostCap,
                title = stringResource(MR.strings.pref_player_audio_boost_cap),
                subtitle = boostCap.toString(),
                min = 0,
                max = 200,
                onValueChanged = {
                    boostCapPref.set(it)
                    true
                },
            ),
        )
    }
}