package com.kodastream.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.subtitles.SubtitleStyleDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper

/**
 * Pure conversion function converting double-tap seek time in seconds to milliseconds,
 * matching how [com.lagradost.cloudstream3.ui.player.PlayerView] and
 * [com.lagradost.cloudstream3.ui.player.PlayerGestureHelper] interpret [R.string.double_tap_seek_time_key].
 */
fun seekTimeMsFromSeconds(s: Int): Long = s.toLong() * 1000L

private const val PREFERRED_AUDIO_LANGUAGE_KEY = "preferred_audio_language"

private val SEEK_TIME_OPTIONS = listOf(5, 10, 15, 20, 25, 30, 45, 60)
private val DECODING_OPTIONS = listOf(-1, 0, 1, 2)
private val BUFFER_SIZE_OPTIONS = listOf(0, 10, 25, 50, 100, 200)
private val BUFFER_DISK_OPTIONS = listOf(0, 100, 250, 500, 1000, 2000)
private val BUFFER_LENGTH_OPTIONS = listOf(0, 15, 30, 60, 120, 300)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val speedupKey = stringResource(R.string.speedup_key)
    val doubleTapKey = stringResource(R.string.double_tap_enabled_key)
    val doubleTapPauseKey = stringResource(R.string.double_tap_pause_enabled_key)
    val seekTimeKey = stringResource(R.string.double_tap_seek_time_key)
    val autoplayNextKey = stringResource(R.string.autoplay_next_key)
    val autoRotateKey = stringResource(R.string.auto_rotate_video_key)
    val pipEnabledKey = stringResource(R.string.pip_enabled_key)

    val softwareDecodingKey = stringResource(R.string.software_decoding_key)
    val bufferSizeKey = stringResource(R.string.video_buffer_size_key)
    val bufferDiskKey = stringResource(R.string.video_buffer_disk_key)
    val bufferLengthKey = stringResource(R.string.video_buffer_length_key)
    val previewSeekbarKey = stringResource(R.string.preview_seekbar_key)
    val subtitlesEncodingKey = stringResource(R.string.subtitles_encoding_key)
    val preferredAudioLangKey = PREFERRED_AUDIO_LANGUAGE_KEY

    var holdToSpeedUp by remember { mutableStateOf(AppPrefs.holdToSpeedUp(context)) }
    var doubleTapEnabled by remember { mutableStateOf(prefs.getBoolean(doubleTapKey, false)) }
    var doubleTapPause by remember { mutableStateOf(prefs.getBoolean(doubleTapPauseKey, false)) }
    var seekTime by remember { mutableIntStateOf(prefs.getInt(seekTimeKey, 10)) }
    var autoplayNext by remember { mutableStateOf(prefs.getBoolean(autoplayNextKey, true)) }
    var autoRotate by remember { mutableStateOf(prefs.getBoolean(autoRotateKey, true)) }
    var pipEnabled by remember { mutableStateOf(prefs.getBoolean(pipEnabledKey, true)) }

    var softwareDecoding by remember { mutableIntStateOf(prefs.getInt(softwareDecodingKey, -1)) }
    var bufferSize by remember { mutableIntStateOf(prefs.getInt(bufferSizeKey, 0)) }
    var bufferDisk by remember { mutableIntStateOf(prefs.getInt(bufferDiskKey, 0)) }
    var bufferLength by remember { mutableIntStateOf(prefs.getInt(bufferLengthKey, 0)) }
    var previewSeekbar by remember { mutableStateOf(prefs.getBoolean(previewSeekbarKey, true)) }

    var subtitlesEncoding by remember { mutableStateOf(prefs.getString(subtitlesEncodingKey, null)) }
    var preferredAudioLang by remember {
        mutableStateOf(prefs.getString(preferredAudioLangKey, null) ?: CS3IPlayer.preferredAudioTrackLanguage)
    }

    var seekDialog by remember { mutableStateOf(false) }
    var decodingDialog by remember { mutableStateOf(false) }
    var bufferSizeDialog by remember { mutableStateOf(false) }
    var bufferDiskDialog by remember { mutableStateOf(false) }
    var bufferLengthDialog by remember { mutableStateOf(false) }
    var encodingDialog by remember { mutableStateOf(false) }
    var audioLangDialog by remember { mutableStateOf(false) }

    val encodingNames = remember { context.resources.getStringArray(R.array.subtitles_encoding_list) }
    val encodingValues = remember { context.resources.getStringArray(R.array.subtitles_encoding_values) }

    val audioLanguages = remember {
        val distinct = SubtitleHelper.languages
            .filter { it.ISO_639_1.isNotBlank() }
            .distinctBy { it.ISO_639_1 }
            .sortedBy { it.languageName }
        listOf<String?>(null) + distinct.map { it.ISO_639_1 }
    }

    CenteredListBox { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .semantics { heading() },
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }

            item(key = "playback") {
                val count = 7
                SettingsGroup(stringResource(R.string.settings_playback)) {
                    SwitchRow(
                        index = 0,
                        count = count,
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.settings_hold_speedup),
                        supporting = stringResource(R.string.settings_hold_speedup_summary),
                        checked = holdToSpeedUp,
                        onCheckedChange = { value ->
                            AppPrefs.setHoldToSpeedUp(context, value)
                            holdToSpeedUp = value
                        },
                    )
                    SwitchRow(
                        index = 1,
                        count = count,
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.player_double_tap_seek),
                        supporting = stringResource(R.string.player_double_tap_seek_summary),
                        checked = doubleTapEnabled,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(doubleTapKey, value) }
                            doubleTapEnabled = value
                        },
                    )
                    SwitchRow(
                        index = 2,
                        count = count,
                        icon = Icons.Rounded.PauseCircle,
                        title = stringResource(R.string.player_double_tap_pause),
                        supporting = stringResource(R.string.player_double_tap_pause_summary),
                        checked = doubleTapPause,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(doubleTapPauseKey, value) }
                            doubleTapPause = value
                        },
                    )
                    NavigationRow(
                        index = 3,
                        count = count,
                        icon = Icons.Rounded.AvTimer,
                        title = stringResource(R.string.player_seek_time),
                        supporting = stringResource(R.string.player_seek_time_format, seekTime),
                        onClick = { seekDialog = true },
                    )
                    SwitchRow(
                        index = 4,
                        count = count,
                        icon = Icons.Rounded.SkipNext,
                        title = stringResource(R.string.player_autoplay_next),
                        supporting = stringResource(R.string.player_autoplay_next_summary),
                        checked = autoplayNext,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(autoplayNextKey, value) }
                            autoplayNext = value
                        },
                    )
                    SwitchRow(
                        index = 5,
                        count = count,
                        icon = Icons.Rounded.ScreenRotation,
                        title = stringResource(R.string.player_auto_rotate),
                        supporting = stringResource(R.string.player_auto_rotate_summary),
                        checked = autoRotate,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(autoRotateKey, value) }
                            autoRotate = value
                        },
                    )
                    SwitchRow(
                        index = 6,
                        count = count,
                        icon = Icons.Rounded.PictureInPicture,
                        title = stringResource(R.string.player_pip),
                        supporting = stringResource(R.string.player_pip_summary),
                        checked = pipEnabled,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(pipEnabledKey, value) }
                            pipEnabled = value
                        },
                    )
                }
            }

            item(key = "video") {
                val count = 5
                SettingsGroup(stringResource(R.string.settings_video)) {
                    NavigationRow(
                        index = 0,
                        count = count,
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.player_decoding_mode),
                        supporting = decodingLabel(softwareDecoding),
                        onClick = { decodingDialog = true },
                    )
                    NavigationRow(
                        index = 1,
                        count = count,
                        icon = Icons.Rounded.Storage,
                        title = stringResource(R.string.player_buffer_size),
                        supporting = if (bufferSize == 0) {
                            stringResource(R.string.player_buffer_default)
                        } else {
                            stringResource(R.string.player_buffer_mb_format, bufferSize)
                        },
                        onClick = { bufferSizeDialog = true },
                    )
                    NavigationRow(
                        index = 2,
                        count = count,
                        icon = Icons.Rounded.Save,
                        title = stringResource(R.string.player_buffer_disk),
                        supporting = if (bufferDisk == 0) {
                            stringResource(R.string.player_buffer_default)
                        } else {
                            stringResource(R.string.player_buffer_mb_format, bufferDisk)
                        },
                        onClick = { bufferDiskDialog = true },
                    )
                    NavigationRow(
                        index = 3,
                        count = count,
                        icon = Icons.Rounded.Timer,
                        title = stringResource(R.string.player_buffer_length),
                        supporting = if (bufferLength == 0) {
                            stringResource(R.string.player_buffer_default)
                        } else {
                            stringResource(R.string.player_buffer_sec_format, bufferLength)
                        },
                        onClick = { bufferLengthDialog = true },
                    )
                    SwitchRow(
                        index = 4,
                        count = count,
                        icon = Icons.Rounded.Tv,
                        title = stringResource(R.string.player_preview_seekbar),
                        supporting = stringResource(R.string.player_preview_seekbar_summary),
                        checked = previewSeekbar,
                        onCheckedChange = { value ->
                            prefs.edit { putBoolean(previewSeekbarKey, value) }
                            previewSeekbar = value
                        },
                    )
                }
            }

            item(key = "subtitles") {
                val count = 3
                val encodingIndex = encodingValues.indexOf(subtitlesEncoding).takeIf { it >= 0 } ?: 0
                val currentEncodingLabel = encodingNames.getOrNull(encodingIndex) ?: stringResource(R.string.automatic)
                val currentAudioLangLabel = audioLanguageLabel(preferredAudioLang)

                SettingsGroup(stringResource(R.string.settings_subtitles_audio)) {
                    NavigationRow(
                        index = 0,
                        count = count,
                        icon = Icons.Rounded.Subtitles,
                        title = stringResource(R.string.player_subtitles_encoding),
                        supporting = currentEncodingLabel,
                        onClick = { encodingDialog = true },
                    )
                    NavigationRow(
                        index = 1,
                        count = count,
                        icon = Icons.Rounded.Audiotrack,
                        title = stringResource(R.string.player_preferred_audio_lang),
                        supporting = currentAudioLangLabel,
                        onClick = { audioLangDialog = true },
                    )
                    NavigationRow(
                        index = 2,
                        count = count,
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.player_subtitles_style),
                        supporting = stringResource(R.string.player_subtitles_style_summary),
                        onClick = {
                            val activity = context.findActivity() as? FragmentActivity
                            activity?.let {
                                SubtitleStyleDialog().show(it.supportFragmentManager, "SubtitleStyle")
                            }
                        },
                    )
                }
            }
        }
    }

    if (seekDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_seek_time),
            options = SEEK_TIME_OPTIONS,
            selected = seekTime,
            icon = Icons.Rounded.AvTimer,
            label = { s -> stringResource(R.string.player_seek_time_format, s) },
            onSelect = { s ->
                prefs.edit { putInt(seekTimeKey, s) }
                seekTime = s
                seekDialog = false
            },
            onDismiss = { seekDialog = false },
        )
    }

    if (decodingDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_decoding_mode),
            options = DECODING_OPTIONS,
            selected = softwareDecoding,
            icon = Icons.Rounded.Memory,
            label = { mode -> decodingLabel(mode) },
            onSelect = { mode ->
                prefs.edit { putInt(softwareDecodingKey, mode) }
                softwareDecoding = mode
                decodingDialog = false
            },
            onDismiss = { decodingDialog = false },
        )
    }

    if (bufferSizeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_buffer_size),
            options = BUFFER_SIZE_OPTIONS,
            selected = bufferSize,
            icon = Icons.Rounded.Storage,
            label = { size ->
                if (size == 0) {
                    stringResource(R.string.player_buffer_default)
                } else {
                    stringResource(R.string.player_buffer_mb_format, size)
                }
            },
            onSelect = { size ->
                prefs.edit { putInt(bufferSizeKey, size) }
                bufferSize = size
                bufferSizeDialog = false
            },
            onDismiss = { bufferSizeDialog = false },
        )
    }

    if (bufferDiskDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_buffer_disk),
            options = BUFFER_DISK_OPTIONS,
            selected = bufferDisk,
            icon = Icons.Rounded.Save,
            label = { size ->
                if (size == 0) {
                    stringResource(R.string.player_buffer_default)
                } else {
                    stringResource(R.string.player_buffer_mb_format, size)
                }
            },
            onSelect = { size ->
                prefs.edit { putInt(bufferDiskKey, size) }
                bufferDisk = size
                bufferDiskDialog = false
            },
            onDismiss = { bufferDiskDialog = false },
        )
    }

    if (bufferLengthDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_buffer_length),
            options = BUFFER_LENGTH_OPTIONS,
            selected = bufferLength,
            icon = Icons.Rounded.Timer,
            label = { len ->
                if (len == 0) {
                    stringResource(R.string.player_buffer_default)
                } else {
                    stringResource(R.string.player_buffer_sec_format, len)
                }
            },
            onSelect = { len ->
                prefs.edit { putInt(bufferLengthKey, len) }
                bufferLength = len
                bufferLengthDialog = false
            },
            onDismiss = { bufferLengthDialog = false },
        )
    }

    if (encodingDialog) {
        val selectedIndex = encodingValues.indexOf(subtitlesEncoding).takeIf { it >= 0 } ?: 0
        ChoiceDialog(
            title = stringResource(R.string.player_subtitles_encoding),
            options = encodingValues.indices.toList(),
            selected = selectedIndex,
            icon = Icons.Rounded.Subtitles,
            label = { i -> encodingNames.getOrNull(i) ?: stringResource(R.string.automatic) },
            onSelect = { i ->
                val v = encodingValues[i]
                prefs.edit {
                    if (v.isEmpty()) remove(subtitlesEncodingKey) else putString(subtitlesEncodingKey, v)
                }
                subtitlesEncoding = v.ifEmpty { null }
                encodingDialog = false
            },
            onDismiss = { encodingDialog = false },
        )
    }

    if (audioLangDialog) {
        ChoiceDialog(
            title = stringResource(R.string.player_preferred_audio_lang),
            options = audioLanguages,
            selected = preferredAudioLang,
            icon = Icons.Rounded.Audiotrack,
            label = { code -> audioLanguageLabel(code) },
            onSelect = { code ->
                prefs.edit {
                    if (code == null) remove(preferredAudioLangKey) else putString(preferredAudioLangKey, code)
                }
                CS3IPlayer.preferredAudioTrackLanguage = code
                preferredAudioLang = code
                audioLangDialog = false
            },
            onDismiss = { audioLangDialog = false },
        )
    }
}

@Composable
private fun decodingLabel(mode: Int): String = when (mode) {
    0 -> stringResource(R.string.player_decoding_hw_sw)
    1 -> stringResource(R.string.player_decoding_hw_only)
    2 -> stringResource(R.string.player_decoding_sw_hw)
    else -> stringResource(R.string.player_decoding_auto)
}

@Composable
private fun audioLanguageLabel(code: String?): String {
    if (code == null) return stringResource(R.string.automatic)
    val metadata = SubtitleHelper.languages.firstOrNull { it.ISO_639_1.equals(code, ignoreCase = true) }
    return if (metadata != null) {
        "${metadata.nameNextToFlagEmoji()} (${metadata.ISO_639_1})"
    } else {
        code.uppercase()
    }
}
