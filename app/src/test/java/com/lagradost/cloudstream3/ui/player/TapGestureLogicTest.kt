package com.lagradost.cloudstream3.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGestureLogicTest {
    private fun tracker() = DoubleTapTracker(maxGapMs = 200L, maxDistanceFraction = 0.25f)

    private fun tap(
        endedWithUp: Boolean = true,
        hadSwipe: Boolean = false,
        speedUp: Boolean = false,
        moved: Float = 2f,
        hold: Long? = 80L,
    ) = TapGesture.isTap(
        endedWithUp = endedWithUp,
        hadSwipe = hadSwipe,
        speedUpTriggered = speedUp,
        movedPx = moved,
        slopPx = 16f,
        holdTimeMs = hold,
        maxHoldMs = 200L,
    )

    @Test
    fun aQuickStillLiftedTouchIsATap() {
        assertTrue(tap())
        assertTrue(tap(hold = null))
        assertTrue(tap(moved = 16f))
    }

    @Test
    fun aCancelledTouchIsNeverATap() {
        assertFalse(tap(endedWithUp = false))
    }

    @Test
    fun aSwipeOrSpeedUpIsNotATap() {
        assertFalse(tap(hadSwipe = true))
        assertFalse(tap(speedUp = true))
    }

    @Test
    fun aDragBeyondTheSlopIsNotATap() {
        assertFalse(tap(moved = 16.5f))
    }

    @Test
    fun aLongPressIsNotATap() {
        assertFalse(tap(hold = 200L))
        assertFalse(tap(hold = 900L))
    }

    @Test
    fun theFirstTapIsNeverADoubleTap() {
        assertFalse(tracker().registerTap(x = 500f, viewWidth = 1000, nowMs = 10_000L))
    }

    @Test
    fun twoCloseQuickTapsFormADoubleTap() {
        val t = tracker()
        assertFalse(t.registerTap(500f, 1000, 10_000L))
        assertTrue(t.registerTap(520f, 1000, 10_150L))
    }

    @Test
    fun aSlowSecondTapStartsANewSequence() {
        val t = tracker()
        assertFalse(t.registerTap(500f, 1000, 10_000L))
        assertFalse(t.registerTap(500f, 1000, 10_200L))
        assertTrue(t.registerTap(500f, 1000, 10_350L))
    }

    @Test
    fun tapsOnOppositeSidesAreNotADoubleTap() {
        val t = tracker()
        assertFalse(t.registerTap(100f, 1000, 10_000L))
        assertFalse(t.registerTap(900f, 1000, 10_100L))
    }

    @Test
    fun theDistanceLimitIsAQuarterOfTheWidth() {
        val t = tracker()
        assertFalse(t.registerTap(400f, 1000, 10_000L))
        assertTrue(t.registerTap(650f, 1000, 10_100L))
        val u = tracker()
        assertFalse(u.registerTap(400f, 1000, 10_000L))
        assertFalse(u.registerTap(651f, 1000, 10_100L))
    }

    @Test
    fun clearingAfterASwipeOrCancelForgetsThePreviousTap() {
        val t = tracker()
        assertFalse(t.registerTap(500f, 1000, 10_000L))
        t.clear()
        assertFalse(t.registerTap(500f, 1000, 10_050L))
    }

    @Test
    fun rapidRepeatedTapsKeepCountingAsDoubleTaps() {
        val t = tracker()
        assertFalse(t.registerTap(500f, 1000, 10_000L))
        assertTrue(t.registerTap(500f, 1000, 10_100L))
        assertTrue(t.registerTap(505f, 1000, 10_200L))
        assertTrue(t.registerTap(495f, 1000, 10_300L))
    }

    @Test
    fun aClockThatMovesBackwardsNeverCountsAsADoubleTap() {
        val t = tracker()
        assertFalse(t.registerTap(500f, 1000, 10_000L))
        assertFalse(t.registerTap(500f, 1000, 9_900L))
    }

    @Test
    fun theFullSequenceSwipeThenTapThenTapBehavesAsExpected() {
        val t = tracker()
        // First touch is a swipe: not a tap, so the caller clears.
        assertFalse(tap(hadSwipe = true))
        t.clear()
        // A quick tap right after the swipe is only a first tap...
        assertTrue(tap())
        assertFalse(t.registerTap(300f, 1000, 20_000L))
        // ...and the next one completes the double-tap.
        assertTrue(tap())
        assertTrue(t.registerTap(310f, 1000, 20_120L))
    }
}
