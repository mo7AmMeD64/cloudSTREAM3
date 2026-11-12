package com.kodastream.app.manga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLanguageFilterTest {
    @Test
    fun allShowsEveryLanguage() {
        val filter = MangaLanguageFilter(all = true, languages = setOf("en"))

        assertTrue(filter.matches("ar"))
        assertTrue(filter.matches("en"))
        assertTrue(filter.matches(null))
    }

    @Test
    fun oneLanguageHidesTheOthers() {
        val filter = MangaLanguageFilter(all = false, languages = setOf("ar"))

        assertTrue(filter.matches("ar"))
        assertFalse(filter.matches("en"))
    }

    @Test
    fun multiLanguageAndUnknownStayVisible() {
        val filter = MangaLanguageFilter(all = false, languages = setOf("ar"))

        assertTrue(filter.matches("all"))
        assertTrue(filter.matches(""))
        assertTrue(filter.matches(null))
    }
}
