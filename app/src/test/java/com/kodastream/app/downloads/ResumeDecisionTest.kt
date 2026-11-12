package com.kodastream.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeDecisionTest {

    @Test
    fun startOffsetZeroWith200ReturnsAppend() {
        // Normal fresh download starting from empty file
        assertEquals(ResumeAction.APPEND, resumeDecision(0L, 200))
    }

    @Test
    fun startOffsetZeroWith206ReturnsAppend() {
        // Server responded 206 for byte 0
        assertEquals(ResumeAction.APPEND, resumeDecision(0L, 206))
    }

    @Test
    fun startOffsetZeroWith404ReturnsError() {
        assertEquals(ResumeAction.ERROR, resumeDecision(0L, 404))
    }

    @Test
    fun startOffsetZeroWith500ReturnsError() {
        assertEquals(ResumeAction.ERROR, resumeDecision(0L, 500))
    }

    @Test
    fun startOffsetPositiveWith206ReturnsAppend() {
        // Standard Range response: append to existing partial file
        assertEquals(ResumeAction.APPEND, resumeDecision(1024L, 206))
        assertEquals(ResumeAction.APPEND, resumeDecision(52_428_800L, 206))
    }

    @Test
    fun startOffsetPositiveWith200ReturnsRestartFromZero() {
        // Server ignored Range header: must recreate target and restart from 0
        assertEquals(ResumeAction.RESTART_FROM_ZERO, resumeDecision(1024L, 200))
        assertEquals(ResumeAction.RESTART_FROM_ZERO, resumeDecision(52_428_800L, 200))
    }

    @Test
    fun otherResponseCodesReturnError() {
        // Non-200/206 HTTP codes are errors
        assertEquals(ResumeAction.ERROR, resumeDecision(1024L, 416))
        assertEquals(ResumeAction.ERROR, resumeDecision(1024L, 403))
        assertEquals(ResumeAction.ERROR, resumeDecision(1024L, 404))
        assertEquals(ResumeAction.ERROR, resumeDecision(1024L, 500))
        assertEquals(ResumeAction.ERROR, resumeDecision(1024L, 503))
    }

    @Test
    fun negativeStartOffsetReturnsError() {
        assertEquals(ResumeAction.ERROR, resumeDecision(-1L, 200))
        assertEquals(ResumeAction.ERROR, resumeDecision(-1L, 206))
    }
}
