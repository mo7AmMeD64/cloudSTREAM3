package com.lagradost.cloudstream3.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerQueueIndexTest {
    @Test
    fun clampKeepsAValidIndex() {
        assertEquals(0, clampQueueIndex(0, 5))
        assertEquals(3, clampQueueIndex(3, 5))
        assertEquals(4, clampQueueIndex(4, 5))
    }

    @Test
    fun clampPullsAnOutOfRangeIndexBackIntoTheQueue() {
        assertEquals(4, clampQueueIndex(5, 5))
        assertEquals(4, clampQueueIndex(99, 5))
        assertEquals(0, clampQueueIndex(-3, 5))
    }

    @Test
    fun anEmptyOrSingleQueueAlwaysMapsToZero() {
        assertEquals(0, clampQueueIndex(7, 0))
        assertEquals(0, clampQueueIndex(-1, 0))
        assertEquals(0, clampQueueIndex(7, 1))
    }

    @Test
    fun previousGoesBackwardsAndStopsAtTheFirstEpisode() {
        assertEquals(2, previousQueueIndex(3))
        assertEquals(0, previousQueueIndex(1))
        assertNull(previousQueueIndex(0))
    }

    @Test
    fun nextGoesForwardsAndStopsAtTheLastEpisode() {
        assertEquals(1, nextQueueIndex(0, 3))
        assertEquals(2, nextQueueIndex(1, 3))
        assertNull(nextQueueIndex(2, 3))
        assertNull(nextQueueIndex(0, 1))
        assertNull(nextQueueIndex(0, 0))
    }

    @Test
    fun walkingBackFromTheEndNeverLeavesTheQueue() {
        var index = clampQueueIndex(Int.MAX_VALUE, 4)
        val seen = mutableListOf(index)
        while (true) {
            index = previousQueueIndex(index) ?: break
            seen += index
        }
        assertEquals(listOf(3, 2, 1, 0), seen)
    }
}
