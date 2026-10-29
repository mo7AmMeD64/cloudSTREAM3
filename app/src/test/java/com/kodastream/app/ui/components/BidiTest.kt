package com.kodastream.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiTest {
    @Test
    fun wrapsWholeTitleInFirstStrongIsolates() {
        val title = "Spider-Man: No Way Home"
        assertEquals("\u2068$title\u2069", bidiSafe(title))
    }

    @Test
    fun keepsMixedDirectionTextAndPunctuationInsideTheIsolate() {
        val title = "Fast & Furious 9 - سرعة"
        val wrapped = bidiSafe(title)
        assertEquals('\u2068', wrapped.first())
        assertEquals('\u2069', wrapped.last())
        assertTrue(wrapped.contains(title))
    }
}
