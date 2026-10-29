package com.kodastream.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsVariantSelectionTest {

    private val baseUrl = "https://example.com/live/master.m3u8"

    @Test
    fun standalonePlaylistSelectsHighestResolution() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=750000,RESOLUTION=640x360
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1400000,AVERAGE-BANDWIDTH=1300000,RESOLUTION=854x480
            480p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2800000,AVERAGE-BANDWIDTH=2600000,RESOLUTION=1280x720
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,AVERAGE-BANDWIDTH=4800000,RESOLUTION=1920x1080
            1080p.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/1080p.m3u8", selected)
    }

    @Test
    fun standalonePlaylistTieBreaksByAverageBitrate() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=4500000,AVERAGE-BANDWIDTH=4000000,RESOLUTION=1920x1080
            1080p_low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,AVERAGE-BANDWIDTH=5500000,RESOLUTION=1920x1080
            1080p_high.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/1080p_high.m3u8", selected)
    }

    @Test
    fun mixedPlaylistFiltersSeparateAudioAndPicksBestStandalone() {
        // High-bandwidth 1080p stream references external audio track (URI declared).
        // 720p stream has muxed/standalone audio.
        // selectDownloadVariant must pick 720p to prevent silent video download.
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-main",NAME="English",DEFAULT=YES,URI="audio/prog_index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,AVERAGE-BANDWIDTH=7500000,RESOLUTION=1920x1080,AUDIO="audio-main"
            1080p_no_audio.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,AVERAGE-BANDWIDTH=2800000,RESOLUTION=1280x720
            720p_standalone.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/720p_standalone.m3u8", selected)
    }

    @Test
    fun audioOnlyVariantPrefersVideoWhenAvailable() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            audio_only.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
            video_720p.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/video_720p.m3u8", selected)
    }

    @Test
    fun audioOnlyPlaylistSelectsHighestBandwidthAudio() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=64000,CODECS="mp4a.40.2"
            audio_low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2"
            audio_high.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/audio_high.m3u8", selected)
    }

    @Test
    fun fallbackWhenNoVariantsAreStandalone() {
        // Both variants have separate audio tracks. Neither is standalone playable.
        // Fallback kicks in and picks the highest BANDWIDTH variant.
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-main",NAME="English",DEFAULT=YES,URI="audio/prog_index.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1280x720,AUDIO="audio-main"
            720p_no_audio.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,AUDIO="audio-main"
            1080p_no_audio.m3u8
        """.trimIndent()

        val selected = selectDownloadVariant(playlist, baseUrl)
        assertEquals("https://example.com/live/1080p_no_audio.m3u8", selected)
    }

    @Test
    fun nonMasterPlaylistReturnsNull() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:10.0,
            segment0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val selected = selectDownloadVariant(mediaPlaylist, baseUrl)
        assertNull(selected)
    }
}
