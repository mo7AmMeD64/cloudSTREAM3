package com.kodastream.app.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaSourceResolverTest {
    private fun source(
        id: Long,
        name: String,
        language: String = "en",
        nsfw: Boolean = false,
    ) = SourceChoice(id = id, name = name, language = language, nsfw = nsfw)

    @Test
    fun rememberedSourceWins() {
        val sources = listOf(source(1, "One"), source(2, "Two"))

        assertEquals(2L, selectSource(sources, 2L)?.id)
    }

    @Test
    fun hiddenRememberedSourceFallsBackToTheFirstVisible() {
        val sources = listOf(source(1, "Hidden", nsfw = true), source(2, "Visible"))

        assertEquals(2L, selectSource(sources, 1L)?.id)
    }

    @Test
    fun missingOrAbsentSourceFallsBackToTheFirstVisible() {
        val sources = listOf(source(1, "One"), source(2, "Two"))

        assertEquals(1L, selectSource(sources, 99L)?.id)
        assertEquals(1L, selectSource(sources, null)?.id)
    }

    @Test
    fun noVisibleSourceSelectsNothing() {
        assertNull(selectSource(listOf(source(1, "Hidden", nsfw = true)), 1L))
        assertNull(selectSource(emptyList(), null))
    }

    @Test
    fun groupsPutPinnedThenAppLanguageThenOthers() {
        val sources = listOf(
            source(1, "Pinned B", language = "ja"),
            source(2, "Pinned A", language = "en"),
            source(3, "English"),
            source(4, "Japanese", language = "ja"),
            source(5, "Unknown", language = ""),
        )

        val groups = groupSources(
            sources = sources,
            appLanguage = "en",
            pinnedIds = setOf(1L, 2L),
        )

        assertEquals(listOf("", "en", "ja", "other"), groups.map { it.language })
        assertTrue(groups.first().pinned)
        assertEquals(listOf("Pinned A", "Pinned B"), groups.first().sources.map { it.name })
        assertEquals(listOf("English"), groups[1].sources.map { it.name })
        assertEquals(listOf("Japanese"), groups[2].sources.map { it.name })
        assertEquals(listOf("Unknown"), groups[3].sources.map { it.name })
    }

    @Test
    fun groupsDropNsfwAndRepeatedIds() {
        val sources = listOf(
            source(1, "Visible"),
            source(1, "Visible again"),
            source(2, "Hidden", nsfw = true),
        )

        val groups = groupSources(sources, "en", emptySet())

        assertEquals(listOf("Visible"), groups.flatMap { it.sources }.map { it.name })
    }

    @Test
    fun noPinnedGroupWhenNothingIsPinned() {
        val groups = groupSources(listOf(source(1, "One")), "en", emptySet())

        assertTrue(groups.none { it.pinned })
        assertEquals(listOf("One"), groups.single().sources.map { it.name })
    }
}
