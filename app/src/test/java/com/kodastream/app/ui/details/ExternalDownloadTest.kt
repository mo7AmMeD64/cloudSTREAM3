package com.kodastream.app.ui.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDownloadTest {
    private val managers = listOf(
        DownloadManager("idm.internet.download.manager.plus", "1DM+"),
        DownloadManager("com.dv.adm", "ADM"),
        DownloadManager("idm.internet.download.manager", "1DM"),
    )

    @Test
    fun fileNameUsesTitleLabelAndUrlExtension() {
        assertEquals(
            "Spider-Man No Way Home - S01E02.mkv",
            downloadFileName(
                title = "Spider-Man: No Way Home",
                episodeLabel = "S01E02",
                url = "https://cdn.example.com/a/b/movie.mkv?token=1",
                isM3u8 = false,
            ),
        )
    }

    @Test
    fun fileNameFallsBackToVideoAndM3u8() {
        assertEquals(
            "video.m3u8",
            downloadFileName(title = " ", episodeLabel = null, url = "https://x/y", isM3u8 = true),
        )
    }

    @Test
    fun fileNameDoesNotTrustWeirdUrlExtensions() {
        assertEquals(
            "Title.mp4",
            downloadFileName(title = "Title", episodeLabel = null, url = "https://x/y.verylongext", isM3u8 = false),
        )
    }

    @Test
    fun headerLinesKeepOrderAndAddRefererOnce() {
        assertEquals(
            listOf("User-Agent: UA", "Cookie: a=b", "Referer: https://ref"),
            downloadHeaderLines(
                linkedMapOf("User-Agent" to "UA", "Cookie" to "a=b"),
                referer = "https://ref",
            ),
        )
        assertEquals(
            listOf("referer: https://ref"),
            downloadHeaderLines(mapOf("referer" to "https://ref"), referer = "https://other"),
        )
    }

    @Test
    fun blankRefererAddsNoLine() {
        assertTrue(downloadHeaderLines(emptyMap(), referer = " ").isEmpty())
    }

    @Test
    fun headerValueIsCaseInsensitive() {
        assertEquals("UA", headerValue(mapOf("user-agent" to "UA"), "User-Agent"))
    }

    @Test
    fun rememberedManagerComesFirstAndRestKeepOrder() {
        assertEquals(
            listOf("com.dv.adm", "idm.internet.download.manager.plus", "idm.internet.download.manager"),
            orderDownloadManagers(managers, remembered = "com.dv.adm").map { it.packageName },
        )
    }

    @Test
    fun unknownOrMissingRememberedChoiceKeepsOrder() {
        assertEquals(managers, orderDownloadManagers(managers, remembered = null))
        assertEquals(managers, orderDownloadManagers(managers, remembered = "not.installed"))
    }

    @Test
    fun onlyHttpUrlsAreDownloadable() {
        assertTrue(isHttpUrl("https://example.com/a.m3u8"))
        assertTrue(isHttpUrl("HTTP://example.com"))
        assertFalse(isHttpUrl("ftp://example.com/a.mp4"))
        assertFalse(isHttpUrl("content://media/1"))
        assertFalse(isHttpUrl(""))
    }
}
