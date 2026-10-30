package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalCastTest {

    @Test
    fun mimeTypeResolvesM3u8AndMp4Correctly() {
        assertEquals("application/x-mpegURL", wvcMimeType(ExtractorLinkType.M3U8))
        assertEquals("video/*", wvcMimeType(ExtractorLinkType.VIDEO))
        assertEquals("video/*", wvcMimeType(ExtractorLinkType.DASH))
    }

    @Test
    fun buildWvcHeadersPreservesEntriesAndSanitizesNewlines() {
        val input = linkedMapOf(
            "User-Agent" to "Custom/1.0\r\n",
            "Cookie" to "session=abc",
            "" to "ignored",
        )
        val result = buildWvcHeadersMap(input, referer = null)

        assertEquals("Custom/1.0  ", result["User-Agent"])
        assertEquals("session=abc", result["Cookie"])
        assertFalse(result.containsKey(""))
    }

    @Test
    fun buildWvcHeadersAppendsRefererWhenMissing() {
        val input = mapOf("Authorization" to "Bearer token")
        val result = buildWvcHeadersMap(input, referer = "https://stream.provider.com/watch")

        assertEquals("Bearer token", result["Authorization"])
        assertEquals("https://stream.provider.com/watch", result["Referer"])
    }

    @Test
    fun buildWvcHeadersDoesNotDuplicateCaseInsensitiveReferer() {
        val input = mapOf("referer" to "https://existing.ref")
        val result = buildWvcHeadersMap(input, referer = "https://new.ref")

        assertEquals(1, result.size)
        assertEquals("https://existing.ref", result["referer"])
    }

    @Test
    fun buildWvcHeadersIgnoresBlankReferer() {
        val result = buildWvcHeadersMap(emptyMap(), referer = "   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun prepareWvcCastParamsExtractsAllFields() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "SuperProvider",
            name = "Fast Server",
            url = "https://cdn.example.com/playlist.m3u8",
            referer = "https://cdn.example.com",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.M3U8,
            headers = mapOf("User-Agent" to "Mozilla/5.0"),
        )
        val params = prepareWvcCastParams(link, title = "Episode 1: The Beginning")

        assertEquals("https://cdn.example.com/playlist.m3u8", params.url)
        assertEquals("application/x-mpegURL", params.mimeType)
        assertEquals("Episode 1: The Beginning", params.title)
        assertEquals("com.instantbits.cast.webvideo", params.packageName)
        assertEquals("Mozilla/5.0", params.headers["User-Agent"])
        assertEquals("https://cdn.example.com", params.headers["Referer"])
    }

    @Test
    fun prepareWvcCastParamsFallsBackToLinkNameWhenTitleMissing() {
        @Suppress("DEPRECATION")
        val link = ExtractorLink(
            source = "SuperProvider",
            name = "Default Title",
            url = "https://cdn.example.com/video.mp4",
            referer = "",
            quality = Qualities.P1080.value,
            type = ExtractorLinkType.VIDEO,
            headers = emptyMap(),
        )
        val params = prepareWvcCastParams(link)
        assertEquals("Default Title", params.title)
        assertEquals("video/*", params.mimeType)
    }

    @Test
    fun packageAndStoreConstantsTargetWebVideoCaster() {
        assertEquals("com.instantbits.cast.webvideo", WVC_PACKAGE_NAME)
        assertTrue(WVC_MARKET_URI.contains(WVC_PACKAGE_NAME))
        assertTrue(WVC_PLAY_STORE_URL.contains(WVC_PACKAGE_NAME))
    }

    @Test
    fun urlCompatibilityForCastingChecksHttp() {
        assertTrue(isHttpUrl("https://example.com/video.mp4"))
        assertTrue(isHttpUrl("http://example.com/stream.m3u8"))
        assertFalse(isHttpUrl("content://media/external/video/media/1"))
        assertFalse(isHttpUrl("file:///storage/emulated/0/video.mp4"))
    }
}
