package com.kodastream.app.ui.player

import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.pickAutoSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSubtitleTest {

    private fun sub(
        language: String,
        code: String?,
        downloaded: Boolean = false,
    ) = SubtitleData(
        originalName = language,
        nameSuffix = "",
        url = "file:///subtitle-${language}.srt",
        origin = if (downloaded) SubtitleOrigin.DOWNLOADED_FILE else SubtitleOrigin.URL,
        mimeType = "application/x-subrip",
        headers = emptyMap(),
        languageCode = code,
    )

    @Test
    fun savedLanguageWins() {
        val english = sub("English", "en")
        val arabic = sub("Arabic", "ar")
        assertEquals(
            english,
            pickAutoSubtitle(setOf(arabic, english), "en", settings = true, downloads = true),
        )
    }

    @Test
    fun downloadedSubtitleWinsOverOnlineSameLanguage() {
        val onlineEnglish = sub("English", "en")
        val downloadedEnglish = sub("English", "en", downloaded = true)
        assertEquals(
            downloadedEnglish,
            pickAutoSubtitle(setOf(onlineEnglish, downloadedEnglish), "en", true, true),
        )
    }

    @Test
    fun noLanguageMatchFallsBackToFirstSubtitle() {
        val arabic = sub("Arabic", "ar")
        val french = sub("French", "fr")
        assertEquals(
            arabic,
            pickAutoSubtitle(setOf(french, arabic), "en", settings = true, downloads = true),
        )
    }

    @Test
    fun downloadedSubtitleIsPreferredWhenNothingMatchesTheLanguage() {
        val onlineFrench = sub("French", "fr")
        val downloadedArabic = sub("Arabic", "ar", downloaded = true)
        assertEquals(
            downloadedArabic,
            pickAutoSubtitle(setOf(onlineFrench, downloadedArabic), "en", true, true),
        )
    }

    @Test
    fun downloadsOnlySelectionUsesTheDownloadedSubtitle() {
        val onlineEnglish = sub("English", "en")
        val downloadedArabic = sub("Arabic", "ar", downloaded = true)
        assertEquals(
            downloadedArabic,
            pickAutoSubtitle(setOf(onlineEnglish, downloadedArabic), "en", false, true),
        )
    }

    @Test
    fun blankLanguageMeansNoSubtitles() {
        assertNull(
            pickAutoSubtitle(setOf(sub("English", "en")), "", settings = true, downloads = true),
        )
    }

    @Test
    fun nullLanguageSelectsNothing() {
        assertNull(
            pickAutoSubtitle(setOf(sub("English", "en")), null, settings = true, downloads = true),
        )
    }

    @Test
    fun emptySubtitleListSelectsNothing() {
        assertNull(pickAutoSubtitle(emptySet(), "en", settings = true, downloads = true))
    }
}
