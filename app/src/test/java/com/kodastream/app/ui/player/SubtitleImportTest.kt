package com.kodastream.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleImportTest {
    @Test
    fun acceptsSubtitleExtensions() {
        assertEquals("srt", SubtitleImport.extension("movie.SRT"))
        assertEquals("vtt", SubtitleImport.extension("movie.vtt"))
        assertEquals("ass", SubtitleImport.extension("movie.ass"))
        assertEquals("", SubtitleImport.extension("movie.mp4"))
    }

    @Test
    fun buildsCacheNameAndDropsDirectoriesAndUnicode() {
        assertEquals("42_movie.en.srt", SubtitleImport.fileName("movie.en.srt", 42))
        assertEquals("42_subtitle.ass", SubtitleImport.fileName("ترجمة.ass", 42))
        assertNull(SubtitleImport.fileName("movie.mp4", 42))
        assertNull(SubtitleImport.fileName("noextension", 42))
    }
}
