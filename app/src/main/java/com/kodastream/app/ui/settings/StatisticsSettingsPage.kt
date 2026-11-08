package com.kodastream.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.WatchStatsStore
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.data.migrateLegacyMovieMarkers
import com.kodastream.app.data.watchDurationParts
import com.kodastream.app.data.watchedEpisodeCount
import com.kodastream.app.data.watchedMovieCount
import com.kodastream.app.data.watchedSeriesCount

/**
 * Watch statistics: real viewing time (counted only while the player plays) and how
 * many movies, episodes and series were watched, derived from the watched markers.
 */
@Composable
internal fun StatisticsSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppPrefs.watchStatisticsEnabled(context)) }
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    var totalWatchMs by remember { mutableStateOf(WatchStatsStore.current(context)) }

    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { keys -> if (keys != null) watchedKeys = keys }
    }
    // Movies finished in the player before 0.12.0 were stored as episode 0 of season 0.
    // Count them as the movies they are, without touching the stored markers.
    val countedKeys = remember(watchedKeys) {
        val movieTitles = HistoryStore.current(context)
            .filter { it.type == "movie" }
            .map { it.key }
            .toSet()
        migrateLegacyMovieMarkers(watchedKeys, movieTitles)
    }
    LaunchedEffect(enabled) {
        if (enabled) {
            totalWatchMs = WatchStatsStore.current(context)
            WatchStatsStore.totalMs.collect { total -> if (total != null) totalWatchMs = total }
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_cat_statistics),
        onBack = onBack,
    ) {
        item(key = "watch_statistics") {
            val duration = watchDurationParts(totalWatchMs)
            val watchTimeLabel = when {
                duration.years > 0L -> stringResource(
                    R.string.watch_statistics_time_value_years,
                    duration.years, duration.months, duration.days, duration.hours, duration.minutes,
                )
                duration.months > 0L -> stringResource(
                    R.string.watch_statistics_time_value_months,
                    duration.months, duration.days, duration.hours, duration.minutes,
                )
                duration.days > 0L -> stringResource(
                    R.string.watch_statistics_time_value_days,
                    duration.days, duration.hours, duration.minutes,
                )
                else -> stringResource(
                    R.string.watch_statistics_time_value,
                    duration.hours, duration.minutes,
                )
            }
            val rowCount = if (enabled) 5 else 1
            SettingsGroup(null) {
                SwitchRow(
                    index = 0,
                    count = rowCount,
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.settings_watch_statistics_enabled),
                    supporting = stringResource(R.string.settings_watch_statistics_enabled_summary),
                    checked = enabled,
                    onCheckedChange = { value ->
                        AppPrefs.setWatchStatisticsEnabled(context, value)
                        enabled = value
                    },
                )
                if (enabled) {
                    InfoRow(
                        index = 1,
                        count = rowCount,
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.watch_statistics_time),
                        supporting = watchTimeLabel,
                    )
                    InfoRow(
                        index = 2,
                        count = rowCount,
                        icon = Icons.Rounded.Visibility,
                        title = stringResource(R.string.watch_statistics_episodes),
                        supporting = stringResource(
                            R.string.watch_statistics_count,
                            watchedEpisodeCount(countedKeys),
                        ),
                    )
                    InfoRow(
                        index = 3,
                        count = rowCount,
                        icon = Icons.Rounded.Theaters,
                        title = stringResource(R.string.watch_statistics_movies),
                        supporting = stringResource(
                            R.string.watch_statistics_count,
                            watchedMovieCount(countedKeys),
                        ),
                    )
                    InfoRow(
                        index = 4,
                        count = rowCount,
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(R.string.watch_statistics_series),
                        supporting = stringResource(
                            R.string.watch_statistics_count,
                            watchedSeriesCount(countedKeys),
                        ),
                    )
                }
            }
        }
    }
}
