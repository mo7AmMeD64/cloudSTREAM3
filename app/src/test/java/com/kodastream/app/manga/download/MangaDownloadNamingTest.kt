package com.kodastream.app.manga.download

import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDownloadNamingTest {
    @Test
    fun chapterFolderUsesScanlatorAndChapterName() {
        val chapter = MangaChapter(name = "Chapter 12", scanlator = "Team A")

        assertEquals("Team A_Chapter 12", MangaDownloadProvider.chapterDirName(chapter))
    }

    @Test
    fun chapterFolderFallsBackToTheChapterName() {
        val chapter = MangaChapter(name = "Chapter 12")

        assertEquals("Chapter 12", MangaDownloadProvider.chapterDirName(chapter))
    }

    @Test
    fun blankChapterNameBecomesChapter() {
        assertEquals("Chapter", MangaDownloadProvider.chapterDirName(MangaChapter(name = "")))
    }

    @Test
    fun fatSeparatorsAreReplaced() {
        val chapter = MangaChapter(name = "12: the/end?")

        assertEquals("12_ the_end_", MangaDownloadProvider.chapterDirName(chapter))
    }

    @Test
    fun sourceFolderUsesTheStoredSourceName() {
        val manga = MangaEntry(sourceId = 7, sourceName = "MangaDex")

        assertEquals("MangaDex", MangaDownloadProvider.sourceDirName(manga))
    }

    @Test
    fun sourceFolderFallsBackToTheSourceId() {
        val manga = MangaEntry(sourceId = 7)

        assertEquals("source-7", MangaDownloadProvider.sourceDirName(manga))
    }

    @Test
    fun mangaFolderIsSanitized() {
        assertEquals("Title_ Part 1", MangaDownloadProvider.mangaDirName("Title: Part 1"))
    }

    @Test
    fun imageNamesAreRecognized() {
        assertTrue(MangaDiskUtil.isImageName("001.JPG"))
        assertTrue(MangaDiskUtil.isImageName("cover.webp"))
        assertFalse(MangaDiskUtil.isImageName("001.tmp"))
        assertFalse(MangaDiskUtil.isImageName(null))
    }

    @Test
    fun imageTypeIsReadFromTheHeader() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0)

        assertEquals("png", MangaDiskUtil.findImageExtension(png.inputStream()))
        assertEquals("jpg", MangaDiskUtil.findImageExtension(jpeg.inputStream()))
    }
}
