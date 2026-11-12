package com.kodastream.app.ui.search

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeItem(
    override val url: String,
    override val apiName: String = "test",
) : SearchResponse {
    override val name: String = url
    override var type: TvType? = null
    override var posterUrl: String? = null
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var quality: SearchQuality? = null
    override var score: Score? = null
}

class SearchPaginationTest {

    // Requirement 6: fun shouldShowLoadMore(lastPages: Map<String, Boolean>): Boolean
    // -> true if any provider has hasNext = true.

    @Test
    fun shouldShowLoadMoreReturnsFalseForEmptyMap() {
        assertFalse(shouldShowLoadMore(emptyMap()))
    }

    @Test
    fun shouldShowLoadMoreReturnsFalseWhenAllProvidersAreFalse() {
        val lastPages = mapOf("Provider1" to false, "Provider2" to false)
        assertFalse(shouldShowLoadMore(lastPages))
    }

    @Test
    fun shouldShowLoadMoreReturnsTrueWhenOneProviderIsTrue() {
        val lastPages = mapOf("Provider1" to true)
        assertTrue(shouldShowLoadMore(lastPages))
    }

    @Test
    fun shouldShowLoadMoreReturnsTrueWhenMixedProviders() {
        val lastPages = mapOf("Provider1" to false, "Provider2" to true, "Provider3" to false)
        assertTrue(shouldShowLoadMore(lastPages))
    }

    @Test
    fun shouldShowLoadMoreReturnsTrueWhenAllProvidersAreTrue() {
        val lastPages = mapOf("Provider1" to true, "Provider2" to true)
        assertTrue(shouldShowLoadMore(lastPages))
    }

    // appendSearchItems tests: de-duplicating by URL

    @Test
    fun appendSearchItemsPreservesCurrentWhenIncomingIsEmpty() {
        val current = listOf(FakeItem("https://a.com/1"), FakeItem("https://a.com/2"))
        val result = appendSearchItems(current, emptyList())
        assertEquals(current, result)
    }

    @Test
    fun appendSearchItemsAppendsNewItemsInOrder() {
        val current = listOf(FakeItem("https://a.com/1"))
        val incoming = listOf(FakeItem("https://a.com/2"), FakeItem("https://a.com/3"))
        val result = appendSearchItems(current, incoming)
        assertEquals(listOf("https://a.com/1", "https://a.com/2", "https://a.com/3"), result.map { it.url })
    }

    @Test
    fun appendSearchItemsFiltersOutDuplicateUrlsFromCurrentAndIncoming() {
        val current = listOf(FakeItem("https://a.com/1"), FakeItem("https://a.com/2"))
        val incoming = listOf(
            FakeItem("https://a.com/2"), // duplicate from current
            FakeItem("https://a.com/3"), // new
            FakeItem("https://a.com/3"), // duplicate within incoming
        )
        val result = appendSearchItems(current, incoming)
        assertEquals(listOf("https://a.com/1", "https://a.com/2", "https://a.com/3"), result.map { it.url })
    }

    // mergeSearchGroup tests

    @Test
    fun mergeSearchGroupCreatesNewGroupWhenNotFound() {
        val groups = listOf(SearchGroup("ProviderA", listOf(FakeItem("https://a.com/1"))))
        val incoming = listOf(FakeItem("https://b.com/1"))
        val merged = mergeSearchGroup(groups, "ProviderB", incoming)

        assertEquals(2, merged.size)
        assertEquals("ProviderA", merged[0].apiName)
        assertEquals("ProviderB", merged[1].apiName)
        assertEquals(listOf("https://b.com/1"), merged[1].items.map { it.url })
    }

    @Test
    fun mergeSearchGroupAppendsAndDeduplicatesExistingGroup() {
        val groups = listOf(
            SearchGroup("ProviderA", listOf(FakeItem("https://a.com/1"), FakeItem("https://a.com/2"))),
            SearchGroup("ProviderB", listOf(FakeItem("https://b.com/1"))),
        )
        val incoming = listOf(FakeItem("https://a.com/2"), FakeItem("https://a.com/3"))
        val merged = mergeSearchGroup(groups, "ProviderA", incoming)

        assertEquals(2, merged.size)
        assertEquals("ProviderA", merged[0].apiName)
        assertEquals(listOf("https://a.com/1", "https://a.com/2", "https://a.com/3"), merged[0].items.map { it.url })
        assertEquals("ProviderB", merged[1].apiName)
        assertEquals(listOf("https://b.com/1"), merged[1].items.map { it.url })
    }
}
