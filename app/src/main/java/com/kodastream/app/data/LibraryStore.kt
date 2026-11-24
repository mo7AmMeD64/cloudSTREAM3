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

    /**
     * Favorites are per account; signing out leaves the account's copy intact
     * and returns to the guest folder, so two people never see each other's list.
     */
    private fun favoritesFolder(context: Context): String =
        SupabaseAuth.userId(context)?.let { "$FAVORITES@$it" } ?: FAVORITES

    private fun folderFor(context: Context, folder: String): String =
        if (folder == FAVORITES) favoritesFolder(context) else folder

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
    private val cache = StoreCache<List<LibraryItem>>()

    /** Bumped on every change; screens collect it and reload, like PluginManager.revision. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    internal fun notifyChanged() {
        cache.clear()
        _revision.value++
    }

    fun favorites(context: Context): List<LibraryItem> =
        read(context, favoritesFolder(context))

    fun watchlist(context: Context): List<LibraryItem> = read(context, WATCHLIST)

    fun isFavorite(context: Context, apiName: String, url: String): Boolean =
        context.containsKey(favoritesFolder(context), key(apiName, url))

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

    /** Replaces all favorites and watchlist items (data import). */
    fun restore(context: Context, favorites: List<LibraryItem>, watchlist: List<LibraryItem>) {
        val favoritesFolder = favoritesFolder(context)
        context.getKeys(favoritesFolder).forEach { context.removeKey(it) }
        context.getKeys(WATCHLIST).forEach { context.removeKey(it) }
        favorites.forEach { item ->
            context.setKey(favoritesFolder, key(item.apiName, item.url), item)
        }
        watchlist.forEach { item ->
            context.setKey(WATCHLIST, key(item.apiName, item.url), item)
        }
        notifyChanged()
    }

    /** Replaces the active account's favorites only (cloud restore, sign-in hand-off). */
    fun replaceFavorites(context: Context, items: List<LibraryItem>) {
        val folder = favoritesFolder(context)
        context.getKeys(folder).forEach { context.removeKey(it) }
        items.forEach { context.setKey(folder, keyOf(it), it) }
        notifyChanged()
    }

    /** Drops the entry from both lists, whatever it was in (library swipe-away). */
    fun remove(context: Context, item: LibraryItem) = remove(context, listOf(item))

    fun remove(context: Context, items: Collection<LibraryItem>) {
        val favoritesFolder = favoritesFolder(context)
        items.forEach { item ->
            val itemKey = keyOf(item)
            context.removeKey(favoritesFolder, itemKey)
            context.removeKey(WATCHLIST, itemKey)
            FavoriteCategoryStore.forget(context, itemKey)
        }
        if (items.isNotEmpty()) notifyChanged()
    }

    /** Undo support: put favorites back with their original addedAt. */
    fun restore(context: Context, items: Collection<LibraryItem>) {
        val folder = favoritesFolder(context)
        items.forEach { context.setKey(folder, keyOf(it), it) }
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
        val storage = folderFor(context, folder)
        return if (context.containsKey(storage, itemKey)) {
            context.removeKey(storage, itemKey)
            if (storage != WATCHLIST) FavoriteCategoryStore.forget(context, itemKey)
            notifyChanged()
            false
        } else {
            context.setKey(storage, itemKey, item)
            notifyChanged()
            true
        }
    }

    private fun read(context: Context, folder: String): List<LibraryItem> =
        cache.get(folder) {
            context.getKeys(folder)
                .mapNotNull { path -> context.getKey<LibraryItem>(path) }
                .sortedByDescending { it.addedAt }
        }
}
