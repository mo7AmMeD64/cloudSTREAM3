package com.kodastream.app.data

import com.kodastream.app.data.LibraryStore.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteCategoryStoreTest {

    private fun category(id: String, name: String, order: Int) =
        FavoriteCategory(id = id, name = name, order = order, createdAt = order.toLong())

    private fun item(url: String) = LibraryItem(apiName = "api", url = url, name = url)

    /** A small in-memory stand-in for the Android-backed store. */
    private class FakeStore {
        var categories = emptyList<FavoriteCategory>()
        var mappings = emptyMap<String, String>()
        private var nextId = 0
        private var nextTime = 0L

        fun create(rawName: String): CategoryResult {
            val result = FavoriteCategoryLogic.create(
                existing = categories,
                rawName = rawName,
                id = "c${nextId++}",
                now = nextTime++,
            )
            if (result is CategoryResult.Created) categories = categories + result.category
            return result
        }

        fun rename(id: String, rawName: String): CategoryResult {
            val result = FavoriteCategoryLogic.rename(categories, id, rawName)
            if (result is CategoryResult.Renamed) {
                categories = categories.map { if (it.id == id) result.category else it }
            }
            return result
        }

        fun delete(id: String) {
            categories = FavoriteCategoryLogic.delete(categories, id)
            mappings = mappings.filterValues { it != id }
        }

        fun move(keys: Collection<String>, categoryId: String?) {
            mappings = FavoriteCategoryLogic.move(mappings, keys, categoryId)
        }

        fun restore(category: FavoriteCategory, keys: Collection<String>) {
            categories = FavoriteCategoryLogic.restore(categories, category)
            mappings = FavoriteCategoryLogic.move(mappings, keys, category.id)
        }

        fun items(categoryId: String?, favorites: List<LibraryItem>): List<LibraryItem> =
            FavoriteCategoryLogic.itemsIn(
                favorites = favorites,
                mappings = mappings,
                validCategoryIds = categories.map { it.id }.toSet(),
                categoryId = categoryId,
            )
    }

    @Test
    fun namesAreTrimmedAndLimited() {
        assertEquals("Films", FavoriteCategoryLogic.cleanName("  Films  "))
        assertNull(FavoriteCategoryLogic.cleanName("   "))
        assertNull(FavoriteCategoryLogic.cleanName("x".repeat(FavoriteCategoryStore.MAX_NAME_LENGTH + 1)))
        assertTrue(FavoriteCategoryLogic.cleanName("x".repeat(FavoriteCategoryStore.MAX_NAME_LENGTH)) != null)
    }

    @Test
    fun duplicateNamesIgnoreCaseAndScript() {
        val store = FakeStore()
        store.create("Anime")
        assertEquals(CategoryResult.Duplicate, store.create(" anime "))
        store.create("أفلام")
        assertEquals(CategoryResult.Duplicate, store.create("أفلام "))
        assertTrue(store.create("مسلسلات") is CategoryResult.Created)
    }

    @Test
    fun createAppendsToTheEnd() {
        val store = FakeStore()
        store.create("One")
        val second = store.create("Two") as CategoryResult.Created
        assertEquals(1, second.category.order)
    }

    @Test
    fun thirtyCategoriesIsTheLimit() {
        val store = FakeStore()
        repeat(FavoriteCategoryStore.MAX_CATEGORIES) { store.create("Category $it") }
        assertEquals(CategoryResult.LimitReached, store.create("One more"))
    }

    @Test
    fun renameRejectsDuplicatesAndAllowsCaseChange() {
        val store = FakeStore()
        store.create("Anime")
        store.create("Films")
        assertEquals(CategoryResult.Duplicate, store.rename("c1", "anime"))
        val renamed = store.rename("c0", "ANIME") as CategoryResult.Renamed
        assertEquals("ANIME", renamed.category.name)
        assertEquals(CategoryResult.Invalid, store.rename("c0", " "))
        assertEquals(CategoryResult.NotFound, store.rename("missing", "Name"))
    }

    @Test
    fun deletePacksTheRemainingOrder() {
        val store = FakeStore()
        store.create("One")
        store.create("Two")
        store.create("Three")
        store.delete("c1")
        assertEquals(listOf("c0", "c2"), store.categories.map { it.id })
        assertEquals(listOf(0, 1), store.categories.map { it.order })
    }

    @Test
    fun deletingACategoryLeavesItsTitlesUncategorized() {
        val store = FakeStore()
        val created = store.create("Anime") as CategoryResult.Created
        val favorite = item("show")
        store.move(listOf("api|show"), created.category.id)
        assertEquals(listOf(favorite), store.items(created.category.id, listOf(favorite)))

        store.delete(created.category.id)
        assertEquals(emptyList<LibraryItem>(), store.items(created.category.id, listOf(favorite)))
        assertEquals(listOf(favorite), store.items(null, listOf(favorite)))
    }

    @Test
    fun moveChangesAndRemovesTheCategory() {
        val store = FakeStore()
        val first = (store.create("One") as CategoryResult.Created).category
        val second = (store.create("Two") as CategoryResult.Created).category
        store.move(listOf("api|a", "api|b"), first.id)
        store.move(listOf("api|b"), second.id)
        assertEquals(first.id, store.mappings["api|a"])
        assertEquals(second.id, store.mappings["api|b"])
        store.move(listOf("api|a"), null)
        assertNull(store.mappings["api|a"])
    }

    @Test
    fun undoRestoresTheCategoryAndItsTitles() {
        val store = FakeStore()
        val created = store.create("Anime") as CategoryResult.Created
        store.move(listOf("api|a", "api|b"), created.category.id)
        store.delete(created.category.id)

        store.restore(created.category, listOf("api|a", "api|b"))
        assertEquals(created.category, store.categories.single())
        assertEquals(created.category.id, store.mappings["api|a"])
        assertEquals(created.category.id, store.mappings["api|b"])
    }

    @Test
    fun orphanMappingsCountAsUncategorized() {
        val store = FakeStore()
        store.create("Anime")
        store.mappings = mapOf("api|a" to "c0", "api|gone" to "removed-category")
        val favorites = listOf(item("a"), item("gone"))
        assertEquals(listOf(item("a")), store.items("c0", favorites))
        assertEquals(listOf(item("gone")), store.items(null, favorites))
    }

    @Test
    fun selectionTogglesClearsAndSelectsVisible() {
        val selection = FavoriteSelection()
        assertEquals(setOf("a"), selection.toggle("a").keys)
        assertEquals(emptySet<String>(), selection.toggle("a").toggle("a").keys)
        assertEquals(emptySet<String>(), selection.toggle("a").clear().keys)
        assertEquals(setOf("a", "b"), selection.selectAll(listOf("a", "b")).keys)
    }
}
