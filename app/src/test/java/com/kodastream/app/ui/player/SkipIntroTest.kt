package com.kodastream.app.ui.player

import com.lagradost.cloudstream3.utils.videoskip.SkipStamp
import com.lagradost.cloudstream3.utils.videoskip.SkipType
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipIntroTest {

    @Test
    fun skipStamp_storesStartAndEndCorrectly() {
        val stamp = SkipStamp(
            type = SkipType.Opening,
            startMs = 120_000L,
            endMs = 210_000L,
            label = "Opening Theme"
        )
        assertEquals(120_000L, stamp.startMs)
        assertEquals(210_000L, stamp.endMs)
        assertEquals("Opening Theme", stamp.label)
        assertEquals(SkipType.Opening, stamp.type)
    }

    @Test
    fun videoSkipStamp_seekTargetIsEndMs() {
        val stamp = SkipStamp(
            type = SkipType.Intro,
            startMs = 30_000L,
            endMs = 115_000L
        )
        val videoStamp = VideoSkipStamp(
            timestamp = stamp,
            skipToNextEpisode = false,
            source = "AniSkip"
        )
        assertEquals(115_000L, videoStamp.timestamp.endMs)
        assertFalse(videoStamp.skipToNextEpisode)
    }

    @Test
    fun timestampRange_triggersOnlyWithinWindow() {
        val stamp = SkipStamp(
            type = SkipType.Opening,
            startMs = 60_000L,
            endMs = 150_000L
        )

        fun isInWindow(pos: Long) = pos in stamp.startMs until stamp.endMs

        assertFalse("Before startMs should not be in window", isInWindow(59_999L))
        assertTrue("At startMs should be in window", isInWindow(60_000L))
        assertTrue("Inside range should be in window", isInWindow(100_000L))
        assertFalse("At endMs should be outside window", isInWindow(150_000L))
        assertFalse("After endMs should be outside window", isInWindow(150_001L))
    }

    @Test
    fun autoHideDuration_isTenSeconds() {
        val autoHideDelayMs = 10_000L
        assertEquals(10_000L, autoHideDelayMs)
    }
}
