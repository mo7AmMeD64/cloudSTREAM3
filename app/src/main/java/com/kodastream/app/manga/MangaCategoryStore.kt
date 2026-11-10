package com.kodastream.app.manga

import android.content.Context
import com.kodastream.app.data.CategoryResult
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.data.FavoriteCategoryLogic
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.util.UUID

/**
 * Manga categories use the same rules and types as the video ones, but their
 * own `manga_categories` / `manga_category_of` folders. The video store is
 * never touched here.
 */
object MangaCategoryStore {
    internal const val CATEGORIES = "manga_categories"
    internal const val MAPPINGS = "manga_category_of"

    fun categories(context: Context): List<FavoriteCategory> =
        context.getKeys(CATEGORIES)
            .mapNotNull { path -> context.getKey<FavoriteCategory>(path) }
            .sortedWith(compareBy({ it.order }, { it.createdAt }))

    fun mappings(context: Context): Map<String, String> =
        context.getKeys(MAPPINGS).mapNotNull { path ->
            context.getKey<String>(path)?.let { id ->
                path.removePrefix("$MAPPINGS/") to id
            }
        }.toMap()

    fun categoryOf(context: Context, sourceId: Long, url: String): String? =
        context.getKey<String>(MAPPINGS, mangaKey(sourceId, url))

    fun create(context: Context, rawName: String): CategoryResult {
        val result = FavoriteCategoryLogic.create(
            existing = categories(context),
            rawName = rawName,
            id = UUID.randomUUID().toString(),
            now = System.currentTimeMillis(),
        )
        if (result is CategoryResult.Created) {
            context.setKey(CATEGORIES, result.category.id, result.category)
            MangaLibraryStore.notifyChanged()
        }
        return result
    }

    fun rename(context: Context, id: String, rawName: String): CategoryResult {
        val result = FavoriteCategoryLogic.rename(categories(context), id, rawName)
        if (result is CategoryResult.Renamed) {
            context.setKey(CATEGORIES, result.category.id, result.category)
            MangaLibraryStore.notifyChanged()
        }
        return result
    }

    fun delete(context: Context, id: String) {
        val current = categories(context)
        val remaining = FavoriteCategoryLogic.delete(current, id)
        if (remaining.size == current.size) return
        context.removeKey(CATEGORIES, id)
        remaining.forEach { context.setKey(CATEGORIES, it.id, it) }
        context.getKeys(MAPPINGS).forEach { path ->
            if (context.getKey<String>(path) == id) context.removeKey(path)
        }
        MangaLibraryStore.notifyChanged()
    }

    fun move(context: Context, keys: Collection<String>, categoryId: String?) {
        if (keys.isEmpty()) return
        if (categoryId != null && categories(context).none { it.id == categoryId }) return
        setMappings(context, keys.associateWith { categoryId })
    }

    fun applyMappings(context: Context, previous: Map<String, String?>) {
        if (previous.isEmpty()) return
        setMappings(context, previous)
    }

    fun restore(context: Context, category: FavoriteCategory, keys: Collection<String>) {
        FavoriteCategoryLogic.restore(categories(context), category).forEach {
            context.setKey(CATEGORIES, it.id, it)
        }
        keys.forEach { context.setKey(MAPPINGS, it, category.id) }
        MangaLibraryStore.notifyChanged()
    }

    internal fun forget(context: Context, key: String) {
        context.removeKey(MAPPINGS, key)
    }

    private fun setMappings(context: Context, mappings: Map<String, String?>) {
        mappings.forEach { (key, categoryId) ->
            if (categoryId == null) context.removeKey(MAPPINGS, key)
            else context.setKey(MAPPINGS, key, categoryId)
        }
        MangaLibraryStore.notifyChanged()
    }

    /** Drops mappings whose title or category is gone. */
    fun prune(context: Context) {
        val validCategories = categories(context).map { it.id }.toSet()
        val titles = MangaLibraryStore.entries(context).map { it.key() }.toSet()
        context.getKeys(MAPPINGS).forEach { path ->
            val key = path.removePrefix("$MAPPINGS/")
            val categoryId = context.getKey<String>(path)
            if (key !in titles || categoryId == null || categoryId !in validCategories) {
                context.removeKey(path)
            }
        }
    }

    fun itemsIn(
        context: Context,
        entries: List<MangaEntry>,
        categoryId: String?,
    ): List<MangaEntry> {
        val valid = categories(context).map { it.id }.toSet()
        return itemsInCategory(
            items = entries,
            mappings = mappings(context),
            validCategoryIds = valid,
            categoryId = categoryId,
            keyOf = { it.key() },
        )
    }

    /** Counts per category id; null is Uncategorized. */
    fun counts(context: Context, entries: List<MangaEntry>): Map<String?, Int> {
        val valid = categories(context).map { it.id }.toSet()
        val mappings = mappings(context).filterValues { it in valid }
        val result = HashMap<String?, Int>()
        result[null] = entries.count { mappings[it.key()] == null }
        valid.forEach { id -> result[id] = entries.count { mappings[it.key()] == id } }
        return result
    }
}
