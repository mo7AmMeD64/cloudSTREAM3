package com.kodastream.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsMapTest {

    @Test
    fun zeroMapTagsReturnsNullForEmptyPlaylist() {
        assertNull(extractMapUri(""))
    }

    @Test
    fun zeroMapTagsReturnsNullForStandardTsPlaylist() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:10.0,
            segment0.ts
            #EXTINF:10.0,
            segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        assertNull(extractMapUri(playlist))
    }

    @Test
    fun singleMapTagExtractsStandardUri() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            segment0.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals("init.mp4", extractMapUri(playlist))
    }

    @Test
    fun singleMapTagExtractsUriWithTrailingAttributes() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:6.0,
            segment0.mp4
        """.trimIndent()

        assertEquals("init.mp4", extractMapUri(playlist))
    }

    @Test
    fun singleMapTagExtractsUriWithPrecedingAttributes() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MAP:BYTERANGE="720@0",URI="init.mp4"
            #EXTINF:6.0,
            segment0.mp4
        """.trimIndent()

        assertEquals("init.mp4", extractMapUri(playlist))
    }

    @Test
    fun singleMapTagExtractsAbsoluteUri() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MAP:URI="https://cdn.example.com/video/init.mp4"
            #EXTINF:6.0,
            segment0.mp4
        """.trimIndent()

        assertEquals("https://cdn.example.com/video/init.mp4", extractMapUri(playlist))
    }

    @Test
    fun singleMapTagHandlesIndentationAndWhitespace() {
        val playlist = """
            #EXTM3U
               #EXT-X-MAP: URI="subfolder/init.mp4"
            #EXTINF:6.0,
            segment0.mp4
        """.trimIndent()

        assertEquals("subfolder/init.mp4", extractMapUri(playlist))
    }

    @Test
    fun twoMapTagsFirstWins() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MAP:URI="first_init.mp4"
            #EXTINF:6.0,
            segment0.mp4
            #EXT-X-DISCONTINUITY
            #EXT-X-MAP:URI="second_init.mp4"
            #EXTINF:6.0,
            segment1.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        assertEquals("first_init.mp4", extractMapUri(playlist))
    }
}
