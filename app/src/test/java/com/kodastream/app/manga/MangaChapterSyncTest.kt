package com.kodastream.app.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaChapterSyncTest {
    private fun chapter(
        url: String,
        number: Float,
        scanlator: String? = null,
        name: String = "Chapter $number",
    ) = MangaChapter(url = url, name = name, number = number, scanlator = scanlator)

    @Test
    fun sameUrlKeepsTheCachedRowAndTakesTheNewOrder() {
        val cached = listOf(chapter("a", 1f), chapter("b", 2f))
        val fetched = listOf(chapter("b", 2f), chapter("a", 1f))

        val result = syncChapters(cached, fetched)

        assertEquals(listOf("b", "a"), result.chapters.map { it.url })
        assertEquals(listOf(0, 1), result.chapters.map { it.sourceOrder })
        assertTrue(result.aliases.isEmpty())
    }

    @Test
    fun changedUrlMatchesOnNumberAndScanlator() {
        val cached = listOf(chapter("old", 7f, "Group"))
        val fetched = listOf(chapter("new", 7f, "Group"))

        val result = syncChapters(cached, fetched)

        assertEquals(mapOf("new" to "old"), result.aliases)
        assertEquals(listOf("new"), result.chapters.map { it.url })
    }

    @Test
    fun changedUrlWithAnotherScanlatorIsANewChapter() {
        val cached = listOf(chapter("old", 7f, "Group A"))
        val fetched = listOf(chapter("new", 7f, "Group B"))

        val result = syncChapters(cached, fetched)

        assertTrue(result.aliases.isEmpty())
    }

    @Test
    fun oneOldRowCanOnlyAliasOneNewRow() {
        val cached = listOf(chapter("old", 3f, "Group"))
        val fetched = listOf(
            chapter("new-one", 3f, "Group"),
            chapter("new-two", 3f, "Group"),
        )

        val result = syncChapters(cached, fetched)

        assertEquals(mapOf("new-one" to "old"), result.aliases)
        assertEquals(listOf("new-one"), result.chapters.map { it.url })
    }

    @Test
    fun unknownNumbersAreNotAliased() {
        val cached = listOf(chapter("old", -1f))
        val fetched = listOf(chapter("new", -1f))

        assertTrue(syncChapters(cached, fetched).aliases.isEmpty())
    }
}
