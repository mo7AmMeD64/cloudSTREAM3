package com.kodastream.app.crash

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the local crash report needs; null fields are reported as unknown. */
data class CrashReportData(
    val timeMillis: Long = 0L,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val threadName: String? = null,
    val route: String? = null,
    val exitReason: String? = null,
    val throwable: Throwable? = null,
    val diagnostics: List<String> = emptyList(),
)

/** Pure formatter so the report shape is testable without an Android device. */
fun crashReportText(data: CrashReportData): String = buildString {
    appendLine("KodaStream crash report")
    appendLine("Time: ${formatCrashTime(data.timeMillis)}")
    appendLine("Version: ${data.versionName ?: "unknown"} (${data.versionCode ?: "?"})")
    appendLine("Device: ${data.deviceModel ?: "unknown"}")
    appendLine("Android: ${data.androidVersion ?: "unknown"}")
    appendLine("Thread: ${data.threadName ?: "unknown"}")
    appendLine("Screen: ${data.route ?: "unknown"}")
    data.exitReason?.takeIf { it.isNotBlank() }?.let { appendLine("Last exit: $it") }
    appendLine()
    appendLine("Stack trace:")
    appendLine(data.throwable?.stackTraceToString() ?: "not captured")
    if (data.diagnostics.isNotEmpty()) {
        appendLine()
        appendLine("Reader diagnostics (last ${data.diagnostics.size}):")
        data.diagnostics.forEach { appendLine(it) }
    }
}

fun formatCrashTime(millis: Long): String =
    if (millis <= 0L) {
        "unknown"
    } else {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
    }
