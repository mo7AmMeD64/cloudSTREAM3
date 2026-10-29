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
 * What the user has watched, per episode and per movie - a separate store
 * from History, which keeps only one entry per title. Presence of a key
 * means watched; the timestamp keeps when.
 *
 * Keys follow "provider|details url|season|episode" (movies use -1/-1), so a
 * series can track every episode individually while History tracks the title.
 */
object WatchedStore {
    data class WatchedEntry(
        val key: String,
        val watchedAt: Long = 0L,
    )

    private const val STORE_KEY = "watched_items"

    private val _keys = MutableStateFlow<Set<String>?>(null)
    val keys: StateFlow<Set<String>?> = _keys.asStateFlow()

    @Volatile
    private var loaded = false

    /** The watched keys. Loads from disk on the first call, then serves from memory. */
    fun current(context: Context): Set<String> {
        if (!loaded) {
            loaded = true
            _keys.value = context.getKey<List<WatchedEntry>>(STORE_KEY).orEmpty().map { it.key }.toSet()
        }
        return _keys.value.orEmpty()
    }

    fun keyOf(apiName: String, url: String, season: Int?, episode: Int?): String =
        "$apiName|$url|${season ?: -1}|${episode ?: -1}"

    fun isWatched(context: Context, key: String): Boolean = current(context).contains(key)

    fun setAll(context: Context, keys: List<String>, watched: Boolean) {
        val now = current(context).toMutableSet()
        if (watched) keys.forEach { now.add(it) } else keys.forEach { now.remove(it) }
        publish(context, now)
    }

    /**
     * Mark one item and keep History in step: watched makes the History
     * entry read as completed (no progress bar); unwatched clears both the
     * mark and the saved progress so the title starts over. Marking an item
     * that is not in History is fine - the watched store stands on its own.
     */
    fun setWatched(
        context: Context,
        apiName: String,
        url: String,
        season: Int?,
        episode: Int?,
        resumeKey: String?,
        watched: Boolean,
    ) {
        setAll(context, listOf(keyOf(apiName, url, season, episode)), watched)
        val historyKey = "$apiName|$url"
        if (watched) {
            HistoryStore.setCompleted(context, historyKey, true)
        } else {
            HistoryStore.setCompleted(context, historyKey, false)
            HistoryStore.resetProgress(context, historyKey)
            resumeKey?.let { AppPrefs.setPosition(context, it, 0L) }
        }
    }

    private fun publish(context: Context, keys: Set<String>) {
        _keys.value = keys
        val now = System.currentTimeMillis()
        val entries = keys.map { WatchedEntry(key = it, watchedAt = now) }
        scope.launch { context.setKey(STORE_KEY, entries) }
    }
}

/** Serialization stays off the main thread, the same as HistoryStore. */
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
