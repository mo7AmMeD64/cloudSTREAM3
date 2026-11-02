package com.kodastream.app.manga

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.containsKey
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The manga library, kept apart from the video favorites in its own
 * `manga_library` folder. Keys are `"$sourceId|$url"`; the Mihon source id is
 * stable across extension updates, unlike the package name.
 */
object MangaLibraryStore {
    internal const val LIBRARY = "manga_library"

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    internal fun notifyChanged() {
        _revision.value++
    }

    fun entries(context: Context): List<MangaEntry> =
        context.getKeys(LIBRARY)
            .mapNotNull { path -> context.getKey<MangaEntry>(path) }
            .filter { it.url.isNotBlank() }
            .sortedByDescending { it.addedAt }

    fun isFavorite(context: Context, sourceId: Long, url: String): Boolean =
        context.containsKey(LIBRARY, mangaKey(sourceId, url))

    /** Adds when missing, removes when present; returns the new state. */
    fun toggle(context: Context, ref: MangaRef): Boolean {
        if (isFavorite(context, ref.sourceId, ref.url)) {
            remove(context, ref.sourceId, ref.url)
            return false
        }
        add(context, ref)
        return true
    }

    fun add(context: Context, ref: MangaRef) {
        val key = mangaKey(ref.sourceId, ref.url)
        val existing = context.getKey<MangaEntry>(LIBRARY, key)
        if (existing != null) return
        context.setKey(
            LIBRARY,
            key,
            MangaEntry(
                sourceId = ref.sourceId,
                url = ref.url,
                title = ref.title,
                thumbnailUrl = ref.thumbnailUrl,
                addedAt = System.currentTimeMillis(),
            ),
        )
        notifyChanged()
    }

    /** Writes the record as-is; used by details after a fresh update. */
    fun upsert(context: Context, entry: MangaEntry) {
        if (entry.url.isBlank()) return
        context.setKey(LIBRARY, entry.key(), entry)
        notifyChanged()
    }

    /** A details refresh updates the cached copy without touching addedAt. */
    fun mergeDetails(context: Context, entry: MangaEntry) {
        val key = entry.key()
        val old = context.getKey<MangaEntry>(LIBRARY, key) ?: return
        context.setKey(
            LIBRARY,
            key,
            entry.copy(
                addedAt = old.addedAt,
                lastReadAt = old.lastReadAt,
                lastChecked = old.lastChecked,
            ),
        )
        notifyChanged()
    }

    fun setLastRead(context: Context, sourceId: Long, url: String, at: Long) {
        val key = mangaKey(sourceId, url)
        val entry = context.getKey<MangaEntry>(LIBRARY, key) ?: return
        if (entry.lastReadAt >= at) return
        context.setKey(LIBRARY, key, entry.copy(lastReadAt = at))
        notifyChanged()
    }

    fun setLastChecked(context: Context, sourceId: Long, url: String, at: Long) {
        val key = mangaKey(sourceId, url)
        val entry = context.getKey<MangaEntry>(LIBRARY, key) ?: return
        context.setKey(LIBRARY, key, entry.copy(lastChecked = at))
        notifyChanged()
    }

    fun remove(context: Context, sourceId: Long, url: String) {
        context.removeKey(LIBRARY, mangaKey(sourceId, url))
        MangaCategoryStore.forget(context, mangaKey(sourceId, url))
        notifyChanged()
    }

    fun remove(context: Context, entries: Collection<MangaEntry>) {
        entries.forEach { entry ->
            context.removeKey(LIBRARY, entry.key())
            MangaCategoryStore.forget(context, entry.key())
        }
        if (entries.isNotEmpty()) notifyChanged()
    }

    /** Undo support: restores the records with their original timestamps. */
    fun restore(context: Context, entries: Collection<MangaEntry>) {
        entries.forEach { context.setKey(LIBRARY, it.key(), it) }
        if (entries.isNotEmpty()) notifyChanged()
    }
}
