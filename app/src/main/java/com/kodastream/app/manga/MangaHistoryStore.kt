package com.kodastream.app.manga

import android.content.Context
import com.kodastream.app.data.StoreCache
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The manga history: one row per title, the chapter last read. Separate from
 * the video history folder and format, so the two never overwrite each other.
 */
object MangaHistoryStore {
    internal const val HISTORY = "manga_history"

    private val _revision = MutableStateFlow(0L)
    private val cache = StoreCache<List<MangaHistoryEntry>>()
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun notifyChanged() {
        cache.clear()
        _revision.value++
    }

    fun entries(context: Context): List<MangaHistoryEntry> =
        cache.get("entries") {
            context.getKeys(HISTORY)
                .mapNotNull { path -> context.getKey<MangaHistoryEntry>(path) }
                .filter { it.mangaUrl.isNotBlank() }
                .sortedByDescending { it.readAt }
        }

    /** One entry per title; a newer read replaces the older one. */
    fun record(context: Context, entry: MangaHistoryEntry) {
        if (entry.mangaUrl.isBlank()) return
        context.setKey(HISTORY, entry.key(), entry)
        notifyChanged()
    }

    fun remove(context: Context, entry: MangaHistoryEntry) {
        context.removeKey(HISTORY, entry.key())
        notifyChanged()
    }

    fun remove(context: Context, entries: Collection<MangaHistoryEntry>) {
        entries.forEach { context.removeKey(HISTORY, it.key()) }
        if (entries.isNotEmpty()) notifyChanged()
    }

    fun restore(context: Context, entries: Collection<MangaHistoryEntry>) {
        entries.forEach { context.setKey(HISTORY, it.key(), it) }
        if (entries.isNotEmpty()) notifyChanged()
    }

    fun clear(context: Context) {
        context.removeKeys(HISTORY)
        notifyChanged()
    }
}
