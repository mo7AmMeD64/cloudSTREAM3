package com.kodastream.app.crash

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private fun throwable(message: String, cause: Throwable? = null) =
        RuntimeException(message, cause)

    @Test
    fun longStackIsWrittenInFull() {
        val error = throwable("top")
        error.stackTrace = Array(500) { index ->
            StackTraceElement("com.example.Deep", "frame$index", "Deep.kt", index + 1)
        }

        val text = crashReportText(CrashReportData(throwable = error))

        assertTrue(text.contains("frame0"))
        assertTrue(text.contains("frame499"))
    }

    @Test
    fun everyCauseIsWritten() {
        val root = IllegalStateException("root cause")
        val middle = RuntimeException("middle", root)
        val top = throwable("top", middle)

        val text = crashReportText(CrashReportData(throwable = top))

        assertTrue(text.contains("top"))
        assertTrue(text.contains("Caused by"))
        assertTrue(text.contains("middle"))
        assertTrue(text.contains("root cause"))
    }

    @Test
    fun missingFieldsDoNotThrowAndSayUnknown() {
        val text = crashReportText(CrashReportData())

        assertTrue(text.contains("Version: unknown"))
        assertTrue(text.contains("Device: unknown"))
        assertTrue(text.contains("Thread: unknown"))
        assertTrue(text.contains("Stack trace:"))
        assertTrue(text.contains("not captured"))
    }

    @Test
    fun diagnosticsAreAppendedLast() {
        val text = crashReportText(
            CrashReportData(
                versionName = "0.11.1",
                versionCode = 14,
                diagnostics = listOf("10:00:00.000 page=1 state=Queue host=img.test"),
            ),
        )

        assertTrue(text.contains("Version: 0.11.1 (14)"))
        assertTrue(text.contains("Reader diagnostics (last 1):"))
        assertTrue(text.contains("state=Queue"))
        assertTrue(text.contains("host=img.test"))
    }

    @Test
    fun exitReasonAndScreenAreIncluded() {
        val text = crashReportText(
            CrashReportData(route = "MangaReaderActivity", exitReason = "low memory"),
        )

        assertTrue(text.contains("Screen: MangaReaderActivity"))
        assertTrue(text.contains("Last exit: low memory"))
    }
}
