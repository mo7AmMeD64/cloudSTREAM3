package com.kodastream.app.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashAnimTest {

    @Test
    fun `three second splash plays the full authored timeline`() {
        assertEquals(3.0f, SplashTimeline.DISPLAY_SECONDS, 0.0001f)
        assertEquals(4.4f, SplashTimeline.AUTHORED_SECONDS, 0.0001f)
        assertEquals(2.2f, SplashTimeline.authoredAt(0.5f), 0.0001f)
        assertEquals(4.4f, SplashTimeline.authoredAt(1f), 0.0001f)
    }

    @Test
    fun `eyes drop in one after the other and blink later`() {
        assertEquals(0f, SplashTimeline.state(0.30f).eyeAlphaLeft, 0.0001f)

        val staggering = SplashTimeline.state(0.42f)
        assertTrue("left eye enters first", staggering.eyeAlphaLeft > 0.5f)
        assertEquals(0f, staggering.eyeAlphaRight, 0.0001f)

        assertTrue(SplashTimeline.state(1.02f).blink < 0.3f)
        assertEquals(1f, SplashTimeline.state(1.30f).blink, 0.0001f)
    }

    @Test
    fun `smile draws on then the suspicious squint replaces it`() {
        val smiling = SplashTimeline.state(1.20f)
        assertTrue(smiling.smile in 0.1f..0.9f)
        assertTrue(smiling.smileAlpha > 0.9f)

        val suspicious = SplashTimeline.state(1.80f)
        assertTrue(suspicious.squint > 0.9f)
        assertTrue(suspicious.smirkAlpha > 0.9f)
        assertEquals(0f, suspicious.smileAlpha, 0.0001f)
    }

    @Test
    fun `surprise opens the mouth and shows the marks`() {
        val hit = SplashTimeline.state(2.30f)
        assertTrue("spring overshoots", hit.surprise > 1f)
        assertTrue(hit.mouthOpenAlpha > 0.9f)
        assertTrue(hit.marksAlpha > 0.9f)
        assertTrue(hit.eyeScale > 1.1f)
    }

    @Test
    fun `wordmark letters stagger and the underline follows`() {
        val rising = SplashTimeline.state(3.30f)
        assertEquals(1f, rising.wordReveal[0], 0.0001f)
        assertEquals(0f, rising.wordReveal[5], 0.0001f)
        assertEquals(0f, rising.underline, 0.0001f)

        val settled = SplashTimeline.state(3.55f)
        assertTrue(settled.wordReveal[5] > 0.5f)
        assertTrue(settled.underline > 0.5f)
    }

    @Test
    fun `exit zooms and fades exactly at the end`() {
        val before = SplashTimeline.state(3.90f)
        assertEquals(1f, before.exitAlpha, 0.0001f)
        assertEquals(1f, before.exitScale, 0.0001f)

        val end = SplashTimeline.state(4.40f)
        assertEquals(0f, end.exitAlpha, 0.0001f)
        assertTrue(end.exitScale > 1.05f)
    }
}
