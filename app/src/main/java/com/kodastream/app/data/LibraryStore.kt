package com.kodastream.app.data

import android.content.Context
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.DataStore.containsKey
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favorites and watchlist, kept in CloudStream's own DataStore so the app and
 * the extensions share one settings file. The shape follows the engine's
 * other stores: plain values under a folder, keyed by provider and url.
 */
object LibraryStore {
    private const val FAVORITES = "favorites"
    private const val WATCHLIST = "watchlist"

    private fun key(apiName: String, url: String) = storageKey(apiName, url)

    internal fun storageKey(apiName: String, url: String) = "$apiName|$url"

    fun keyOf(item: LibraryItem) = key(item.apiName, item.url)

    /**
     * What a shelf card needs, copied out of a [SearchResponse] at the moment
     * the entry is saved. Plain Jackson types only: this round-trips through
     * the engine's JSON coder, not kotlinx serialization.
     */
    data class LibraryItem(
        val apiName: String,
        val url: String,
        val name: String,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        /** The rating as a 0..10 float, the form the poster badge renders. */
        val score10: Float? = null,
        /** Newest entries first, whatever order the prefs file lists them in. */
        val addedAt: Long = 0L,
    )

    private val _revision = MutableStateFlow(0L)

    /** Bumped on every change; screens collect it and reload, like PluginManager.revision. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    internal fun notifyChanged() {
        _revision.value++
    }

    fun favorites(context: Context): List<LibraryItem> = read(context, FAVORITES)

    fun watchlist(context: Context): List<LibraryItem> = read(context, WATCHLIST)

    fun isFavorite(context: Context, apiName: String, url: String): Boolean =
        context.containsKey(FAVORITES, key(apiName, url))

    fun isWatchlisted(context: Context, apiName: String, url: String): Boolean =
        context.containsKey(WATCHLIST, key(apiName, url))

    /** Adds when missing, removes when present; returns the new state. */
    fun toggleFavorite(context: Context, item: SearchResponse): Boolean =
        toggle(context, FAVORITES, toLibraryItem(item))

    fun toggleWatchlist(context: Context, item: SearchResponse): Boolean =
        toggle(context, WATCHLIST, toLibraryItem(item))

    /** The same toggles for entries already in store form (details screen). */
    fun toggleFavorite(context: Context, item: LibraryItem): Boolean =
        toggle(context, FAVORITES, item)

    fun toggleWatchlist(context: Context, item: LibraryItem): Boolean =
        toggle(context, WATCHLIST, item)

    /** Drops the entry from both lists, whatever it was in (library swipe-away). */
    fun remove(context: Context, item: LibraryItem) = remove(context, listOf(item))

    fun remove(context: Context, items: Collection<LibraryItem>) {
        items.forEach { item ->
            val itemKey = keyOf(item)
            context.removeKey(FAVORITES, itemKey)
            context.removeKey(WATCHLIST, itemKey)
            FavoriteCategoryStore.forget(context, itemKey)
        }
        if (items.isNotEmpty()) notifyChanged()
    }

    /** Undo support: put favorites back with their original addedAt. */
    fun restore(context: Context, items: Collection<LibraryItem>) {
        items.forEach { context.setKey(FAVORITES, keyOf(it), it) }
        if (items.isNotEmpty()) notifyChanged()
    }

    private fun toLibraryItem(source: SearchResponse) = LibraryItem(
        apiName = source.apiName,
        url = source.url,
        name = source.name,
        posterUrl = source.posterUrl,
        posterHeaders = source.posterHeaders,
        score10 = source.score?.toFloat(10),
        addedAt = System.currentTimeMillis(),
    )

    private fun toggle(context: Context, folder: String, item: LibraryItem): Boolean {
        val itemKey = keyOf(item)
        return if (context.containsKey(folder, itemKey)) {
            context.removeKey(folder, itemKey)
            if (folder == FAVORITES) FavoriteCategoryStore.forget(context, itemKey)
            _revision.value++
            false
        } else {
            context.setKey(folder, itemKey, item)
            _revision.value++
            true
        }
    }

    private fun read(context: Context, folder: String): List<LibraryItem> =
        context.getKeys(folder)
            .mapNotNull { path -> context.getKey<LibraryItem>(path) }
            .sortedByDescending { it.addedAt }
}
