package com.kodastream.app.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLibVersionTest {
    @Test
    fun manifestFloatUsesItsShortestDecimal() {
        assertEquals(1.6, parseLibVersion(1.6f, "1.6.39"))
        assertEquals(1.4, parseLibVersion(1.4f, null))
    }

    @Test
    fun versionNameIsTheFallback() {
        assertEquals(1.6, parseLibVersion(null, "1.6.4"))
        assertEquals(1.4, parseLibVersion(0f, "1.4.9"))
        assertEquals(1.6, parseLibVersion(null, "1.6"))
        assertNull(parseLibVersion(null, "abc"))
        assertNull(parseLibVersion(null, null))
    }

    @Test
    fun supportedVersionsUseATolerance() {
        assertTrue(isSupportedLib(1.4))
        assertTrue(isSupportedLib(1.6))
        assertTrue(isSupportedLib(1.6f.toDouble()))
        assertFalse(isSupportedLib(1.5))
        assertFalse(isSupportedLib(1.7))
        assertFalse(isSupportedLib(2.0))
        assertFalse(isSupportedLib(null))
    }

    @Test
    fun regressionFloatToDoubleIsNotTheSupportedVersion() {
        assertNotEquals(1.6, 1.6f.toDouble())
        assertEquals(1.6, parseLibVersion(1.6f, null))
    }
}
