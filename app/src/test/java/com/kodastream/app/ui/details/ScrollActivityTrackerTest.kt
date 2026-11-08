package com.kodastream.app.ui.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollActivityTrackerTest {
    @Test
    fun oneIdleSurfaceCannotResumeWhileAnotherSurfaceIsScrolling() {
        val tracker = ScrollActivityTracker()
        assertTrue(tracker.setActive("details-main", true))
        assertTrue(tracker.setActive("details-timeline", true))
        assertTrue(tracker.setActive("details-main", false))
        assertTrue(tracker.setActive("details-timeline", true))
        assertFalse(tracker.setActive("details-timeline", false))
    }

    @Test
    fun splitPaneUsesOneAggregateSourceAndCanBeCleared() {
        val tracker = ScrollActivityTracker()
        assertTrue(tracker.setActive("details-split", true))
        assertFalse(tracker.clear())
        assertFalse(tracker.setActive("details-split", false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankScrollSourceIsRejected() {
        ScrollActivityTracker().setActive(" ", true)
    }
}
