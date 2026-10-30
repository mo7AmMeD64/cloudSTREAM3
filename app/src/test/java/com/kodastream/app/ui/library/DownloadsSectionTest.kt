package com.kodastream.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSectionTest {

    @Test
    fun resolveDownloadErrorLabelReturnsActualErrorWhenPresent() {
        assertEquals(
            "HTTP 403 Forbidden",
            resolveDownloadErrorLabel("HTTP 403 Forbidden", fallback = "Failed"),
        )
        assertEquals(
            "Connection reset by peer",
            resolveDownloadErrorLabel("Connection reset by peer", fallback = "Failed"),
        )
    }

    @Test
    fun resolveDownloadErrorLabelFallsBackWhenNull() {
        assertEquals(
            "Download failed",
            resolveDownloadErrorLabel(null, fallback = "Download failed"),
        )
    }

    @Test
    fun resolveDownloadErrorLabelFallsBackWhenBlank() {
        assertEquals(
            "Download failed",
            resolveDownloadErrorLabel("   ", fallback = "Download failed"),
        )
    }
}
