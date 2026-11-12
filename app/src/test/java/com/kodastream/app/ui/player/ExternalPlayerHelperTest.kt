package com.kodastream.app.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerHelperTest {

    @Test
    fun resolvesMimeTypeForM3u8Type() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "TestProvider",
            name = "Test Stream",
            url = "https://example.com/stream",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.M3U8,
        )
        assertEquals("application/x-mpegURL", resolveExternalPlayerMimeType(link))
    }

    @Test
    fun resolvesMimeTypeForM3u8InUrl() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "TestProvider",
            name = "Test Stream",
            url = "https://example.com/playlist.m3u8?token=123",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.VIDEO,
        )
        assertEquals("application/x-mpegURL", resolveExternalPlayerMimeType(link))
    }

    @Test
    fun resolvesMimeTypeForDirectVideo() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "TestProvider",
            name = "Test Stream",
            url = "https://example.com/video.mp4",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.VIDEO,
        )
        assertEquals("video/*", resolveExternalPlayerMimeType(link))
    }

    @Test
    fun prepareExternalPlayerParamsNormalizesValues() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "TestProvider",
            name = "Fallback Title",
            url = "https://example.com/movie.mp4",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.VIDEO,
        )
        val params = prepareExternalPlayerParams(link, title = "Custom Title", startPositionMs = 15000L)
        assertEquals("https://example.com/movie.mp4", params.url)
        assertEquals("video/*", params.mimeType)
        assertEquals("Custom Title", params.title)
        assertEquals(15000L, params.position)
    }

    @Test
    fun prepareExternalPlayerParamsFallsBackToLinkNameWhenTitleBlank() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "TestProvider",
            name = "Fallback Title",
            url = "https://example.com/movie.m3u8",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.M3U8,
        )
        val params = prepareExternalPlayerParams(link, title = "  ", startPositionMs = -100L)
        assertEquals("Fallback Title", params.title)
        assertEquals("application/x-mpegURL", params.mimeType)
        assertEquals(0L, params.position)
    }

    @Test
    fun isHttpUrlRecognizesHttpAndHttps() {
        assertTrue(isHttpUrl("http://example.com/video.mp4"))
        assertTrue(isHttpUrl("https://example.com/stream.m3u8"))
        assertTrue(isHttpUrl("HTTPS://EXAMPLE.COM/STREAM"))
        assertFalse(isHttpUrl("content://media/external/video/media/123"))
        assertFalse(isHttpUrl("file:///storage/emulated/0/video.mp4"))
        assertFalse(isHttpUrl(null))
        assertFalse(isHttpUrl(""))
    }
}
