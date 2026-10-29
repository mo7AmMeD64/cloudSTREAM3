package com.kodastream.app.ui.home

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeItem(override val url: String) : SearchResponse {
    override val name: String = url
    override val apiName: String = "test"
    override var type: TvType? = null
    override var posterUrl: String? = null
    override var posterHeaders: Map<String, String>? = null
    override var id: Int? = null
    override var quality: SearchQuality? = null
    override var score: Score? = null
}

class SectionPagerTest {
    private val current = listOf(FakeItem("1"), FakeItem("2"))

    @Test
    fun appendsNewItemsAndKeepsNextPage() {
        val merged = mergeSectionPage(
            current = current,
            lists = listOf(HomePageList("Trending", listOf(FakeItem("3"), FakeItem("4")))),
            responseHasNext = true,
            sectionName = "Trending",
        )
        assertEquals(listOf("1", "2", "3", "4"), merged.items.map { it.url })
        assertTrue(merged.hasNext)
    }

    @Test
    fun deduplicatesByUrl() {
        val merged = mergeSectionPage(
            current = current,
            lists = listOf(HomePageList("Trending", listOf(FakeItem("2"), FakeItem("3")))),
            responseHasNext = true,
            sectionName = "Trending",
        )
        assertEquals(listOf("1", "2", "3"), merged.items.map { it.url })
        assertTrue(merged.hasNext)
    }

    @Test
    fun singleListIsTheSameSectionEvenWhenRenamed() {
        val merged = mergeSectionPage(
            current = current,
            lists = listOf(HomePageList("More Trending", listOf(FakeItem("3")))),
            responseHasNext = false,
            sectionName = "Trending",
        )
        assertEquals(listOf("1", "2", "3"), merged.items.map { it.url })
        assertFalse(merged.hasNext)
    }

    @Test
    fun failedPageStopsPaging() {
        val merged = mergeSectionPage(current, null, responseHasNext = true, sectionName = "Trending")
        assertEquals(current, merged.items)
        assertFalse(merged.hasNext)
    }

    @Test
    fun pageWithoutNewItemsStopsPaging() {
        val merged = mergeSectionPage(
            current = current,
            lists = listOf(HomePageList("Trending", listOf(FakeItem("1"), FakeItem("2")))),
            responseHasNext = true,
            sectionName = "Trending",
        )
        assertEquals(current, merged.items)
        assertFalse(merged.hasNext)
    }

    @Test
    fun missingListInMultiListResponseStopsPaging() {
        val merged = mergeSectionPage(
            current = current,
            lists = listOf(
                HomePageList("Movies", listOf(FakeItem("3"))),
                HomePageList("Shows", listOf(FakeItem("4"))),
            ),
            responseHasNext = true,
            sectionName = "Trending",
        )
        assertEquals(current, merged.items)
        assertFalse(merged.hasNext)
    }
}
