package com.kodastream.app.manga

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MangaSourceFieldsTest {
    private fun local() = MangaEntry(
        sourceId = 7L,
        url = "https://example.test/manga/fast-break",
        title = "Fast Break",
        thumbnailUrl = "https://example.test/old.jpg",
        author = "Old Author",
        artist = "Old Artist",
        description = "Old description",
        genres = listOf("Action"),
        status = SManga.ONGOING,
        sourceName = "Team X",
        language = "en",
    )

    @Test
    fun remoteWithOnlyDescriptionAndGenreKeepsLocalIdentity() {
        // Exactly what Team X's parser returns: no url and no title.
        val remote = SManga.create().apply {
            description = "Remote description"
            genre = "Comedy, Drama"
        }

        val result = local().withRemoteDetails(remote, isFavorite = false)

        assertEquals(7L, result.sourceId)
        assertEquals("https://example.test/manga/fast-break", result.url)
        assertEquals("Fast Break", result.title)
        assertEquals("Remote description", result.description)
        assertEquals(listOf("Comedy", "Drama"), result.genres)
        assertEquals("Team X", result.sourceName)
    }

    @Test
    fun emptyRemoteChangesNothingVisibleAndDoesNotThrow() {
        val local = local()

        val result = local.withRemoteDetails(SManga.create(), isFavorite = false)

        assertEquals(local.url, result.url)
        assertEquals(local.title, result.title)
        assertEquals(local.thumbnailUrl, result.thumbnailUrl)
        assertEquals(local.author, result.author)
        assertEquals(local.artist, result.artist)
        assertEquals(local.description, result.description)
        assertEquals(local.genres, result.genres)
    }

    @Test
    fun favouriteKeepsItsTitleWhileOtherTitlesTakeTheRemoteOne() {
        val remote = SManga.create().apply { title = "Remote Title" }
        val local = local()

        assertEquals(
            "Fast Break",
            local.withRemoteDetails(remote, isFavorite = true).title,
        )
        assertEquals(
            "Remote Title",
            local.withRemoteDetails(remote, isFavorite = false).title,
        )
    }

    @Test
    fun blankRemoteTitleNeverReplacesTheLocalOne() {
        val remote = SManga.create().apply { title = "   " }

        assertEquals(
            "Fast Break",
            local().withRemoteDetails(remote, isFavorite = false).title,
        )
    }

    @Test
    fun nullAuthorKeepsTheOldAuthor() {
        val remote = SManga.create().apply { author = null }

        assertEquals(
            "Old Author",
            local().withRemoteDetails(remote, isFavorite = false).author,
        )
    }

    @Test
    fun blankThumbnailKeepsTheOldThumbnail() {
        val remote = SManga.create().apply { thumbnail_url = "" }

        assertEquals(
            "https://example.test/old.jpg",
            local().withRemoteDetails(remote, isFavorite = false).thumbnailUrl,
        )
    }

    @Test
    fun unsetUrlAndTitleReadAsAbsent() {
        val manga = SManga.create()

        assertNull(manga.urlOrNull())
        assertEquals("", manga.titleOrEmpty())
    }

    @Test
    fun setFieldsReadNormally() {
        val manga = SManga.create().apply {
            url = "/manga/1"
            title = "Title"
        }

        assertEquals("/manga/1", manga.urlOrNull())
        assertEquals("Title", manga.titleOrEmpty())
        assertNotNull(manga.urlOrNull())
    }
}
