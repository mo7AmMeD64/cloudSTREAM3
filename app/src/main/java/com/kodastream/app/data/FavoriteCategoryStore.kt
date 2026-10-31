package com.kodastream.app.data

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.util.UUID

/** One user-created favorites category; plain Jackson-friendly fields. */
data class FavoriteCategory(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val createdAt: Long = 0L,
)

/** The outcome of creating or renaming, so the dialog can show one inline error. */
sealed interface CategoryResult {
    data class Created(val category: FavoriteCategory) : CategoryResult
    data class Renamed(val category: FavoriteCategory) : CategoryResult
    data object Duplicate : CategoryResult
    data object Invalid : CategoryResult
    data object LimitReached : CategoryResult
    data object NotFound : CategoryResult
}

/**
 * Favorites categories live beside the favorites themselves in CloudStream's
 * DataStore, so backup and restore carry them too. The favorites folder keeps
 * its old format; two new folders hold the categories and the key -> category
 * mapping. Every write bumps [LibraryStore.revision], the one revision the
 * screens already collect.
 */
object FavoriteCategoryStore {
    const val MAX_CATEGORIES = 30
    const val MAX_NAME_LENGTH = 40

    private const val CATEGORIES = "favorite_categories"
    private const val MAPPINGS = "favorite_category_of"

    fun categories(context: Context): List<FavoriteCategory> =
        context.getKeys(CATEGORIES)
            .mapNotNull { path -> context.getKey<FavoriteCategory>(path) }
            .sortedWith(compareBy({ it.order }, { it.createdAt }))

    /** Every mapping, including orphans; callers validate against the live lists. */
    fun mappings(context: Context): Map<String, String> =
        context.getKeys(MAPPINGS).mapNotNull { path ->
            context.getKey<String>(path)?.let { id ->
                path.removePrefix("$MAPPINGS/") to id
            }
        }.toMap()

    fun categoryOf(context: Context, apiName: String, url: String): String? =
        context.getKey<String>(MAPPINGS, LibraryStore.storageKey(apiName, url))

    fun create(context: Context, rawName: String): CategoryResult {
        val result = FavoriteCategoryLogic.create(
            existing = categories(context),
            rawName = rawName,
            id = UUID.randomUUID().toString(),
            now = System.currentTimeMillis(),
        )
        if (result is CategoryResult.Created) {
            context.setKey(CATEGORIES, result.category.id, result.category)
            LibraryStore.notifyChanged()
        }
        return result
    }

    fun rename(context: Context, id: String, rawName: String): CategoryResult {
        val result = FavoriteCategoryLogic.rename(categories(context), id, rawName)
        if (result is CategoryResult.Renamed) {
            context.setKey(CATEGORIES, result.category.id, result.category)
            LibraryStore.notifyChanged()
        }
        return result
    }

    /** Deletes the category only; its favorites become Uncategorized. */
    fun delete(context: Context, id: String) {
        val current = categories(context)
        val remaining = FavoriteCategoryLogic.delete(current, id)
        if (remaining.size == current.size) return
        context.removeKey(CATEGORIES, id)
        writeCategories(context, remaining)
        context.getKeys(MAPPINGS).forEach { path ->
            if (context.getKey<String>(path) == id) context.removeKey(path)
        }
        LibraryStore.notifyChanged()
    }

    /** Moves keys to a category; null removes the mapping (Uncategorized). */
    fun move(context: Context, keys: Collection<String>, categoryId: String?) {
        if (keys.isEmpty()) return
        if (categoryId != null && categories(context).none { it.id == categoryId }) return
        setMappings(context, keys.associateWith { categoryId })
    }

    /** Undo support: put several mappings back exactly as they were. */
    fun applyMappings(context: Context, previous: Map<String, String?>) {
        if (previous.isEmpty()) return
        setMappings(context, previous)
    }

    /** Undo support: recreate a deleted category and reattach its titles. */
    fun restore(context: Context, category: FavoriteCategory, keys: Collection<String>) {
        writeCategories(context, FavoriteCategoryLogic.restore(categories(context), category))
        keys.forEach { context.setKey(MAPPINGS, it, category.id) }
        LibraryStore.notifyChanged()
    }

    /** Removes one mapping without a revision bump; the caller already bumps. */
    internal fun forget(context: Context, key: String) {
        context.removeKey(MAPPINGS, key)
    }

    /** Drops mappings whose favorite or category is gone; safe to call on start. */
    fun prune(context: Context) {
        val validCategories = categories(context).map { it.id }.toSet()
        val favorites = LibraryStore.favorites(context)
            .map { LibraryStore.keyOf(it) }
            .toSet()
        context.getKeys(MAPPINGS).forEach { path ->
            val key = path.removePrefix("$MAPPINGS/")
            val categoryId = context.getKey<String>(path)
            if (key !in favorites || categoryId == null || categoryId !in validCategories) {
                context.removeKey(path)
            }
        }
    }

    private fun setMappings(context: Context, mappings: Map<String, String?>) {
        mappings.forEach { (key, categoryId) ->
            if (categoryId == null) {
                context.removeKey(MAPPINGS, key)
            } else {
                context.setKey(MAPPINGS, key, categoryId)
            }
        }
        LibraryStore.notifyChanged()
    }

    private fun writeCategories(context: Context, categories: List<FavoriteCategory>) {
        categories.forEach { context.setKey(CATEGORIES, it.id, it) }
    }
}

/**
 * The pure half: name rules, ordering, moving and cleanup. Kept free of
 * Android types so the unit tests run on the JVM.
 */
internal object FavoriteCategoryLogic {

    fun cleanName(raw: String): String? {
        val name = raw.trim()
        return name.takeIf { it.isNotEmpty() && it.length <= FavoriteCategoryStore.MAX_NAME_LENGTH }
    }

    fun create(
        existing: List<FavoriteCategory>,
        rawName: String,
        id: String,
        now: Long,
    ): CategoryResult {
        val name = cleanName(rawName) ?: return CategoryResult.Invalid
        if (existing.any { it.name.equals(name, ignoreCase = true) }) return CategoryResult.Duplicate
        if (existing.size >= FavoriteCategoryStore.MAX_CATEGORIES) return CategoryResult.LimitReached
        val order = (existing.maxOfOrNull { it.order } ?: -1) + 1
        return CategoryResult.Created(FavoriteCategory(id, name, order, now))
    }

    fun rename(existing: List<FavoriteCategory>, id: String, rawName: String): CategoryResult {
        val target = existing.firstOrNull { it.id == id } ?: return CategoryResult.NotFound
        val name = cleanName(rawName) ?: return CategoryResult.Invalid
        if (existing.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            return CategoryResult.Duplicate
        }
        return CategoryResult.Renamed(target.copy(name = name))
    }

    fun delete(existing: List<FavoriteCategory>, id: String): List<FavoriteCategory> =
        existing
            .filterNot { it.id == id }
            .sortedWith(compareBy({ it.order }, { it.createdAt }))
            .mapIndexed { index, category -> category.copy(order = index) }

    fun restore(existing: List<FavoriteCategory>, category: FavoriteCategory): List<FavoriteCategory> =
        (existing.filterNot { it.id == category.id } + category)
            .sortedWith(compareBy({ it.order }, { it.createdAt }))
            .mapIndexed { index, restored -> restored.copy(order = index) }

    fun move(
        current: Map<String, String>,
        keys: Collection<String>,
        categoryId: String?,
    ): Map<String, String> =
        if (categoryId == null) current - keys.toSet()
        else current + keys.associateWith { categoryId }

    /**
     * The favorites inside one category. Mappings to a category that no longer
     * exists are ignored, so those titles fall under Uncategorized.
     */
    fun itemsIn(
        favorites: List<LibraryStore.LibraryItem>,
        mappings: Map<String, String>,
        validCategoryIds: Set<String>,
        categoryId: String?,
    ): List<LibraryStore.LibraryItem> {
        val effective = mappings.filterValues { it in validCategoryIds }
        return favorites.filter { effective[LibraryStore.keyOf(it)] == categoryId }
    }
}

/** The selection set used by the favorites grid; pure so it is easy to test. */
data class FavoriteSelection(val keys: Set<String> = emptySet()) {
    fun toggle(key: String): FavoriteSelection =
        copy(keys = if (key in keys) keys - key else keys + key)

    fun clear(): FavoriteSelection = FavoriteSelection()

    fun selectAll(visible: Collection<String>): FavoriteSelection =
        copy(keys = visible.toSet())
}
