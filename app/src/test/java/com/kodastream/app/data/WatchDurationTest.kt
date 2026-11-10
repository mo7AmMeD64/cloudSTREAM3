package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchDurationTest {
    @Test
    fun negativeCorruptDurationIsClampedToZero() {
        assertEquals(WatchDurationParts(0, 0, 0, 0, 0), watchDurationParts(-1L))
    }

    @Test
    fun convertsDaysMonthsAndYearsWithoutOverflowingTheDisplay() {
        val oneYearPlus = ((365L + 61L) * 24L * 60L + 37L) * 60_000L
        assertEquals(WatchDurationParts(1, 2, 1, 0, 37), watchDurationParts(oneYearPlus))
    }

    @Test
    fun keepsShortDurationsInHoursAndMinutes() {
        assertEquals(WatchDurationParts(0, 0, 0, 3, 42), watchDurationParts((3L * 60L + 42L) * 60_000L))
    }

    @Test
    fun startsClockOnlyWhenExoPlayerReportsActualPlayback() {
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = 1_000L, elapsedToAddMs = 0L),
            advancePlaybackClock(null, 1_000L, true),
        )
    }

    @Test
    fun periodicFlushAddsOnlyElapsedWallClockAndKeepsClockActive() {
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = 6_000L, elapsedToAddMs = 5_000L),
            advancePlaybackClock(1_000L, 6_000L, true),
        )
    }

    @Test
    fun pauseClosesAndAccumulatesOnlyTheActiveInterval() {
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = null, elapsedToAddMs = 1_250L),
            advancePlaybackClock(6_000L, 7_250L, false),
        )
    }

    @Test
    fun resumingAfterPauseStartsANewIntervalWithoutCountingThePause() {
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = 20_000L, elapsedToAddMs = 0L),
            advancePlaybackClock(null, 20_000L, true),
        )
    }

    @Test
    fun repeatedPausedEventsAndClockRollbackCannotAddNegativeTime() {
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = null, elapsedToAddMs = 0L),
            advancePlaybackClock(null, 15_000L, false),
        )
        assertEquals(
            PlaybackClockAdvance(activeSinceElapsedMs = null, elapsedToAddMs = 0L),
            advancePlaybackClock(20_000L, 19_000L, false),
        )
    }
}
