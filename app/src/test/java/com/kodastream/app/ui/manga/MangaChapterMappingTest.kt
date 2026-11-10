package com.kodastream.app.ui.manga

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaChapterMappingTest {
    @Test
    fun chapterWithoutUrlIsSkipped() {
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
        }

        assertNull(chapter.toModel(0))
    }

    @Test
    fun blankChapterUrlIsSkipped() {
        val chapter = SChapter.create().apply {
            url = "  "
            name = "Chapter 1"
        }

        assertNull(chapter.toModel(0))
    }

    @Test
    fun missingNameFallsBackToTheChapterNumber() {
        val chapter = SChapter.create().apply {
            url = "/chapter/1"
            chapter_number = 12f
        }

        val model = chapter.toModel(3)

        assertEquals("12", model?.name)
        assertEquals(3, model?.sourceOrder)
    }

    @Test
    fun missingNameAndNumberFallsBackToTheUrlSegment() {
        val chapter = SChapter.create().apply {
            url = "https://example.test/manga/1/chapter-7/"
        }

        assertEquals("chapter-7", chapter.toModel(0)?.name)
    }

    @Test
    fun completeChapterMapsEveryField() {
        val chapter = SChapter.create().apply {
            url = "/chapter/2"
            name = "Chapter 2"
            chapter_number = 2f
            scanlator = "Team"
            date_upload = 123L
        }

        val model = chapter.toModel(1)

        assertEquals("/chapter/2", model?.url)
        assertEquals("Chapter 2", model?.name)
        assertEquals(2f, model?.number)
        assertEquals("Team", model?.scanlator)
        assertEquals(123L, model?.dateUpload)
    }
}
