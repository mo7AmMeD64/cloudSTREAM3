package com.kodastream.app.data

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accumulates wall-clock time only while the player's actual isPlaying state is true.
 * State transitions come from ExoPlayer; periodic flushes limit in-memory exposure.
 */
object WatchStatsStore {
    // Elapsed wall-clock viewing time, counted only while the player really plays.
    // Kept in its own preferences file, separate from the app's other settings.
    private const val PREFS = "kodastream_watch_stats_v2"
    private const val KEY_TOTAL_MS = "total_elapsed_viewing_ms"

    private val _totalMs = MutableStateFlow<Long?>(null)
    val totalMs: StateFlow<Long?> = _totalMs.asStateFlow()

    @Volatile
    private var activeSinceElapsedMs: Long? = null

    fun current(context: Context): Long {
        return _totalMs.value ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TOTAL_MS, 0L).coerceAtLeast(0L).also { _totalMs.value = it }
    }

    /** Called by the real player-state callback; pauses and buffering close the clock interval. */
    @Synchronized
    fun updatePlaybackState(context: Context, isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val advance = advancePlaybackClock(activeSinceElapsedMs, now, isPlaying)
        activeSinceElapsedMs = advance.activeSinceElapsedMs
        addElapsed(context, advance.elapsedToAddMs)
    }

    /** Persist an active interval periodically without stopping the running clock. */
    @Synchronized
    fun flushActive(context: Context) {
        val activeSince = activeSinceElapsedMs ?: return
        val now = SystemClock.elapsedRealtime()
        activeSinceElapsedMs = now
        addElapsed(context, (now - activeSince).coerceAtLeast(0L))
    }

    private fun addElapsed(context: Context, elapsedMs: Long) {
        if (elapsedMs <= 0L) return
        val current = current(context)
        val next = if (Long.MAX_VALUE - current < elapsedMs) Long.MAX_VALUE else current + elapsedMs
        _totalMs.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_TOTAL_MS, next)
        }
    }
}
