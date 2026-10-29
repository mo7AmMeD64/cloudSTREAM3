package com.kodastream.app.data

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the user watched, newest first, one entry per title - for series and
 * anime only the latest watched episode is kept. Stored as one JSON list
 * under CloudStream's DataStore, the same persistence the rest of the app
 * uses, so it survives restarts next to everything else.
 *
 * The in-memory list is the working copy; every change is published to the
 * flow immediately and serialized to disk on a background dispatcher, so
 * recording progress never touches the main thread's frame budget.
 */
object HistoryStore {
    data class HistoryEntry(
        /** "provider|details url" - one entry per title. */
        val key: String,
        val apiName: String,
        /** The details page url, to fall back to when a provider is gone. */
        val url: String,
        val title: String,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        /** movie | series | anime */
        val type: String = "movie",
        val season: Int? = null,
        val episode: Int? = null,
        val episodeTitle: String? = null,
        /** The data string the saved episode plays with. */
        val data: String,
        val subtitle: String? = null,
        val resumeKey: String,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val lastWatchedAt: Long = 0L,
        /** Set by hand from "mark as watched"; progress alone derives it at 90%. */
        val completed: Boolean = false,
    ) {
        /** No progress bar is shown once the item is complete, however it became so. */
        val isComplete: Boolean
            get() = completed || (durationMs > 0 && positionMs >= durationMs * 0.9)
    }

    private const val STORE_KEY = "watch_history"
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<HistoryEntry>?>(null)
    val entries: StateFlow<List<HistoryEntry>?> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    // Serialization of the whole list happens here, never on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The history, newest first. Loads from disk on the first call, then serves from memory. */
    fun current(context: Context): List<HistoryEntry> {
        if (!loaded) {
            loaded = true
            val stored = context.getKey<List<HistoryEntry>>(STORE_KEY) ?: emptyList()
            _entries.value = stored.sortedByDescending { it.lastWatchedAt }
        }
        return _entries.value.orEmpty()
    }

    /**
     * Playback started: replaces any earlier entry for this title (so a
     * series keeps only its latest episode), stamps it, and moves it to the
     * top. The list is trimmed to [MAX_ENTRIES] after every change.
     */
    fun upsert(context: Context, entry: HistoryEntry) {
        val stamped = entry.copy(lastWatchedAt = System.currentTimeMillis())
        val next = buildList {
            add(stamped)
            addAll(current(context).filter { it.key != entry.key })
        }.take(MAX_ENTRIES)
        publish(context, next)
    }

    /** Progress while playing, and on pause/stop/close/background. Order is kept as-is. */
    fun updateProgress(context: Context, key: String, positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        val next = current(context).map { entry ->
            if (entry.key == key) {
                entry.copy(positionMs = positionMs, durationMs = durationMs, lastWatchedAt = now)
            } else {
                entry
            }
        }
        publish(context, next)
    }

    fun remove(context: Context, key: String) {
        publish(context, current(context).filter { it.key != key })
    }

    /** Marks a title completed (or clears the mark) without touching its position. */
    fun setCompleted(context: Context, key: String, completed: Boolean) {
        val next = current(context).map { entry ->
            if (entry.key == key) entry.copy(completed = completed) else entry
        }
        publish(context, next)
    }

    /** Clears a title's saved progress, so unmarking starts it over. */
    fun resetProgress(context: Context, key: String) {
        val next = current(context).map { entry ->
            if (entry.key == key) entry.copy(positionMs = 0) else entry
        }
        publish(context, next)
    }

    fun clear(context: Context) {
        publish(context, emptyList())
    }

    private fun publish(context: Context, next: List<HistoryEntry>) {
        _entries.value = next
        scope.launch { context.setKey(STORE_KEY, next) }
    }
}
