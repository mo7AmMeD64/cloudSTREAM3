package com.kodastream.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOverloadTest {

    @Test
    fun isServerOverloadRecognizesOverloadCodes() {
        assertTrue(isServerOverload(502))
        assertTrue(isServerOverload(503))
        assertTrue(isServerOverload(429))
    }

    @Test
    fun isServerOverloadRejectsNonOverloadCodes() {
        assertFalse(isServerOverload(200))
        assertFalse(isServerOverload(206))
        assertFalse(isServerOverload(400))
        assertFalse(isServerOverload(403))
        assertFalse(isServerOverload(404))
        assertFalse(isServerOverload(500))
        assertFalse(isServerOverload(504))
    }

    @Test
    fun backoffDelaysFollowExponentialSchedule() {
        assertEquals(5, serverOverloadBackoffDelaySeconds(1))
        assertEquals(10, serverOverloadBackoffDelaySeconds(2))
        assertEquals(20, serverOverloadBackoffDelaySeconds(3))
        // Cap/fallback for any higher attempts
        assertEquals(20, serverOverloadBackoffDelaySeconds(4))
    }

    @Test
    fun serverOverloadErrorMessageAdvisesLoweringConcurrency() {
        assertEquals(
            "Server busy (HTTP 502). Please lower 'Max concurrent downloads' in Settings.",
            serverOverloadErrorMessage(502),
        )
        assertEquals(
            "Server busy (HTTP 503). Please lower 'Max concurrent downloads' in Settings.",
            serverOverloadErrorMessage(503),
        )
        assertEquals(
            "Server busy (HTTP 429). Please lower 'Max concurrent downloads' in Settings.",
            serverOverloadErrorMessage(429),
        )
    }

    @Test
    fun serverOverloadExceptionCarriesCodeAndMessage() {
        val ex = DownloadService.ServerOverloadException(502)
        assertEquals(502, ex.code)
        assertEquals("Server overloaded (HTTP 502)", ex.message)
    }
}
