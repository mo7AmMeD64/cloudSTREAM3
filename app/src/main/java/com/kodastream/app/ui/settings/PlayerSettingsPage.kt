package com.kodastream.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs

/**
 * Everything that changes how the video player behaves: playback, touch gestures and
 * which controls are shown. The gesture switches write the preference keys the player's
 * gesture helper already reads, so a change applies on the next playback screen.
 */
@Composable
internal fun PlayerSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current

    var keepScreenOn by remember { mutableStateOf(AppPrefs.keepScreenOn(context)) }
    var holdToSpeedUp by remember { mutableStateOf(AppPrefs.holdToSpeedUp(context)) }

    var verticalSwipe by remember { mutableStateOf(AppPrefs.playerVerticalSwipeEnabled(context)) }
    var horizontalSwipe by remember { mutableStateOf(AppPrefs.playerHorizontalSwipeEnabled(context)) }
    var doubleTap by remember { mutableStateOf(AppPrefs.playerDoubleTapEnabled(context)) }
    var doubleTapPause by remember { mutableStateOf(AppPrefs.playerDoubleTapPauseEnabled(context)) }
    var seekSeconds by remember { mutableStateOf(AppPrefs.playerSeekSeconds(context)) }

    var showSource by remember { mutableStateOf(AppPrefs.playerShowSourceControl(context)) }
    var showLock by remember { mutableStateOf(AppPrefs.playerShowLockControl(context)) }
    var showRotate by remember { mutableStateOf(AppPrefs.playerShowRotateControl(context)) }
    var showResize by remember { mutableStateOf(AppPrefs.playerShowResizeControl(context)) }
    var showSubtitleOffset by remember { mutableStateOf(AppPrefs.playerShowSubtitleOffsetControl(context)) }
    var showSeekButtons by remember { mutableStateOf(AppPrefs.playerShowSeekButtons(context)) }
    var compactControls by remember { mutableStateOf(AppPrefs.playerCompactControls(context)) }
    var progressStyle by remember { mutableStateOf(AppPrefs.playerProgressStyle(context)) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_cat_player),
        onBack = onBack,
    ) {
        item(key = "playback") {
            SettingsGroup(stringResource(R.string.settings_playback)) {
                SwitchRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Rounded.Speed,
                    title = stringResource(R.string.settings_keep_screen_on),
                    supporting = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = keepScreenOn,
                    onCheckedChange = { value ->
                        AppPrefs.setKeepScreenOn(context, value)
                        keepScreenOn = value
                    },
                )
                SwitchRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Rounded.FastForward,
                    title = stringResource(R.string.settings_hold_speedup),
                    supporting = stringResource(R.string.settings_hold_speedup_summary),
                    checked = holdToSpeedUp,
                    onCheckedChange = { value ->
                        AppPrefs.setHoldToSpeedUp(context, value)
                        holdToSpeedUp = value
                    },
                )
            }
        }

        item(key = "gestures") {
            SettingsGroup(stringResource(R.string.settings_player_gestures)) {
                SwitchRow(
                    index = 0,
                    count = 5,
                    icon = Icons.Rounded.SwapVert,
                    title = stringResource(R.string.settings_player_vertical_swipe),
                    supporting = stringResource(R.string.settings_player_vertical_swipe_summary),
                    checked = verticalSwipe,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerVerticalSwipeEnabled(context, value)
                        verticalSwipe = value
                    },
                )
                SwitchRow(
                    index = 1,
                    count = 5,
                    icon = Icons.Rounded.SwapHoriz,
                    title = stringResource(R.string.settings_player_horizontal_swipe),
                    supporting = stringResource(R.string.settings_player_horizontal_swipe_summary),
                    checked = horizontalSwipe,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerHorizontalSwipeEnabled(context, value)
                        horizontalSwipe = value
                    },
                )
                SwitchRow(
                    index = 2,
                    count = 5,
                    icon = Icons.Rounded.TouchApp,
                    title = stringResource(R.string.settings_player_double_tap),
                    supporting = stringResource(R.string.settings_player_double_tap_summary),
                    checked = doubleTap,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerDoubleTapEnabled(context, value)
                        doubleTap = value
                    },
                )
                SwitchRow(
                    index = 3,
                    count = 5,
                    icon = Icons.Rounded.PauseCircle,
                    title = stringResource(R.string.settings_player_double_tap_pause),
                    supporting = stringResource(R.string.settings_player_double_tap_pause_summary),
                    checked = doubleTapPause,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerDoubleTapPauseEnabled(context, value)
                        doubleTapPause = value
                    },
                )
                ChoiceRow(
                    index = 4,
                    count = 5,
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_player_seek_duration),
                    options = AppPrefs.PLAYER_SEEK_SECONDS_OPTIONS,
                    selected = seekSeconds,
                    label = { seconds -> stringResource(R.string.player_seek_seconds, seconds) },
                    onSelect = { seconds ->
                        AppPrefs.setPlayerSeekSeconds(context, seconds)
                        seekSeconds = seconds
                    },
                )
            }
        }

        item(key = "controls") {
            SettingsGroup(stringResource(R.string.settings_player_controls)) {
                SwitchRow(
                    index = 0,
                    count = 8,
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.settings_player_show_source),
                    supporting = stringResource(R.string.settings_player_show_source_summary),
                    checked = showSource,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowSourceControl(context, value)
                        showSource = value
                    },
                )
                SwitchRow(
                    index = 1,
                    count = 8,
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.settings_player_show_lock),
                    supporting = stringResource(R.string.settings_player_show_lock_summary),
                    checked = showLock,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowLockControl(context, value)
                        showLock = value
                    },
                )
                SwitchRow(
                    index = 2,
                    count = 8,
                    icon = Icons.Rounded.ScreenRotation,
                    title = stringResource(R.string.settings_player_show_rotate),
                    supporting = stringResource(R.string.settings_player_show_rotate_summary),
                    checked = showRotate,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowRotateControl(context, value)
                        showRotate = value
                    },
                )
                SwitchRow(
                    index = 3,
                    count = 8,
                    icon = Icons.Rounded.AspectRatio,
                    title = stringResource(R.string.settings_player_show_resize),
                    supporting = stringResource(R.string.settings_player_show_resize_summary),
                    checked = showResize,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowResizeControl(context, value)
                        showResize = value
                    },
                )
                SwitchRow(
                    index = 4,
                    count = 8,
                    icon = Icons.Rounded.Subtitles,
                    title = stringResource(R.string.settings_player_show_subtitle_offset),
                    supporting = stringResource(R.string.settings_player_show_subtitle_offset_summary),
                    checked = showSubtitleOffset,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowSubtitleOffsetControl(context, value)
                        showSubtitleOffset = value
                    },
                )
                SwitchRow(
                    index = 5,
                    count = 8,
                    icon = Icons.Rounded.Forward10,
                    title = stringResource(R.string.settings_player_show_seek_buttons),
                    supporting = stringResource(R.string.settings_player_show_seek_buttons_summary),
                    checked = showSeekButtons,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerShowSeekButtons(context, value)
                        showSeekButtons = value
                    },
                )
                SwitchRow(
                    index = 6,
                    count = 8,
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.settings_player_compact_controls),
                    supporting = stringResource(R.string.settings_player_compact_controls_summary),
                    checked = compactControls,
                    onCheckedChange = { value ->
                        AppPrefs.setPlayerCompactControls(context, value)
                        compactControls = value
                    },
                )
                ChoiceRow(
                    index = 7,
                    count = 8,
                    icon = Icons.Rounded.LinearScale,
                    title = stringResource(R.string.settings_player_progress_style),
                    options = listOf(
                        AppPrefs.PLAYER_PROGRESS_SLIM,
                        AppPrefs.PLAYER_PROGRESS_STANDARD,
                        AppPrefs.PLAYER_PROGRESS_BOLD,
                    ),
                    selected = progressStyle,
                    label = { style ->
                        stringResource(
                            when (style) {
                                AppPrefs.PLAYER_PROGRESS_SLIM -> R.string.player_progress_style_slim
                                AppPrefs.PLAYER_PROGRESS_BOLD -> R.string.player_progress_style_bold
                                else -> R.string.player_progress_style_standard
                            },
                        )
                    },
                    onSelect = { style ->
                        AppPrefs.setPlayerProgressStyle(context, style)
                        progressStyle = style
                    },
                )
            }
        }
    }
}
