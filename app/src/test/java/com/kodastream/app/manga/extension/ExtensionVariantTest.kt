package com.kodastream.app.manga.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionVariantTest {
    @Test
    fun higherVersionCodeWins() {
        assertTrue(preferShared(sharedVersionCode = 10, privateVersionCode = 9))
        assertFalse(preferShared(sharedVersionCode = 9, privateVersionCode = 10))
    }

    @Test
    fun sharedWinsTie() {
        assertTrue(preferShared(sharedVersionCode = 10, privateVersionCode = 10))
    }
}
