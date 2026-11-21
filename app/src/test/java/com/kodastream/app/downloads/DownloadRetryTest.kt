package com.kodastream.app.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryTest {

    @Test
    fun byteProgressResetsTheAttemptCounter() {
        assertTrue(downloadAdvanced(lastBytes = 1_000L, lastSegment = 0, bytes = 1_001L, segment = 0))
        assertTrue(downloadAdvanced(lastBytes = 0L, lastSegment = 0, bytes = 5_000_000L, segment = 0))
    }

    @Test
    fun hlsSegmentProgressResetsTheAttemptCounter() {
        assertTrue(downloadAdvanced(lastBytes = 1_000L, lastSegment = 3, bytes = 1_000L, segment = 4))
    }

    @Test
    fun noProgressKeepsCountingAttempts() {
        assertFalse(downloadAdvanced(lastBytes = 1_000L, lastSegment = 3, bytes = 1_000L, segment = 3))
        assertFalse(downloadAdvanced(lastBytes = 1_000L, lastSegment = 3, bytes = 999L, segment = 2))
    }
}
