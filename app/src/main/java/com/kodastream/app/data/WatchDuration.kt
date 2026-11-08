package com.kodastream.app.data

/** Viewing-time breakdown. Months are normalized to 30 days and years to 365 days. */
internal data class WatchDurationParts(
    val years: Long,
    val months: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
)

internal fun watchDurationParts(totalMillis: Long): WatchDurationParts {
    val totalMinutes = totalMillis.coerceAtLeast(0L) / 60_000L
    val totalDays = totalMinutes / (24L * 60L)
    val years = totalDays / 365L
    val months = (totalDays % 365L) / 30L
    val days = (totalDays % 365L) % 30L
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    return WatchDurationParts(years, months, days, hours, minutes)
}

/**
 * State transition for a monotonic playback clock. It accumulates only intervals
 * bracketed by the player's real isPlaying state, not by media-position samples.
 */
internal data class PlaybackClockAdvance(
    val activeSinceElapsedMs: Long?,
    val elapsedToAddMs: Long,
)

internal fun advancePlaybackClock(
    activeSinceElapsedMs: Long?,
    nowElapsedMs: Long,
    isPlaying: Boolean,
): PlaybackClockAdvance {
    val elapsed = activeSinceElapsedMs?.let { (nowElapsedMs - it).coerceAtLeast(0L) } ?: 0L
    return if (isPlaying) {
        PlaybackClockAdvance(activeSinceElapsedMs = nowElapsedMs, elapsedToAddMs = elapsed)
    } else {
        PlaybackClockAdvance(activeSinceElapsedMs = null, elapsedToAddMs = elapsed)
    }
}
