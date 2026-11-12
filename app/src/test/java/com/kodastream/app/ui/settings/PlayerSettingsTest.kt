package com.kodastream.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSettingsTest {
    @Test
    fun seekTimeMsFromSecondsProducesExpectedMsForPlayerView() {
        // Asserting that setting double_tap_seek_time_key to 30 produces 30_000L for PlayerView
        assertEquals(30_000L, seekTimeMsFromSeconds(30))
    }

    @Test
    fun seekTimeMsFromSecondsMatchesStandardSeekIntervals() {
        assertEquals(5_000L, seekTimeMsFromSeconds(5))
        assertEquals(10_000L, seekTimeMsFromSeconds(10))
        assertEquals(15_000L, seekTimeMsFromSeconds(15))
        assertEquals(20_000L, seekTimeMsFromSeconds(20))
        assertEquals(60_000L, seekTimeMsFromSeconds(60))
    }
}
