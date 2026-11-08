package com.lagradost.cloudstream3.ui.player

import kotlin.math.abs

/**
 * Decision logic for taps on the video surface, kept free of Android types so it
 * can be unit-tested on the JVM. [PlayerGestureHelper] feeds it the facts of a
 * finished touch and acts on the answer.
 */
internal object TapGesture {
    /**
     * True when a finished touch is a genuine tap: the finger was lifted (not a
     * cancelled gesture), no swipe or two-finger gesture took over, the hold-to-speed
     * runnable did not fire, the finger stayed within [slopPx] of where it landed and
     * it was not a long press.
     */
    fun isTap(
        endedWithUp: Boolean,
        hadSwipe: Boolean,
        speedUpTriggered: Boolean,
        movedPx: Float,
        slopPx: Float,
        holdTimeMs: Long?,
        maxHoldMs: Long,
    ): Boolean =
        endedWithUp &&
            !hadSwipe &&
            !speedUpTriggered &&
            movedPx <= slopPx &&
            (holdTimeMs == null || holdTimeMs < maxHoldMs)
}

/**
 * Remembers the last genuine tap so the next one can be classified. Only taps are
 * ever registered; the caller clears it after anything that is not a tap (a swipe,
 * a cancelled gesture, a long press, a locked screen) so those can never be
 * mistaken for the first half of a double-tap.
 */
internal class DoubleTapTracker(
    private val maxGapMs: Long,
    private val maxDistanceFraction: Float,
) {
    private var lastTapAtMs = NO_TAP
    private var lastTapX = 0f

    /**
     * Registers a tap at [x] (in a view [viewWidth] wide) at time [nowMs]. Returns
     * true when it completes a double-tap with the previous tap: quick enough and
     * close enough. The tap is remembered either way, so rapid repeated taps keep
     * counting as double-taps (tap again to keep seeking).
     */
    fun registerTap(x: Float, viewWidth: Int, nowMs: Long): Boolean {
        val gap = nowMs - lastTapAtMs
        val isDouble = lastTapAtMs != NO_TAP &&
            gap in 0L until maxGapMs &&
            abs(x - lastTapX) <= maxDistanceFraction * viewWidth
        lastTapAtMs = nowMs
        lastTapX = x
        return isDouble
    }

    fun clear() {
        lastTapAtMs = NO_TAP
        lastTapX = 0f
    }

    private companion object {
        const val NO_TAP = Long.MIN_VALUE
    }
}
