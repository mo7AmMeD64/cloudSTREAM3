package com.kodastream.app.manga.reader

import eu.kanade.tachiyomi.source.model.Page
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One reader event, kept in memory only and copied into a crash report. */
data class ReaderDiagnosticEvent(
    val timeMillis: Long = System.currentTimeMillis(),
    val chapterUrl: String? = null,
    val pageIndex: Int? = null,
    val state: String? = null,
    val host: String? = null,
    val httpStatus: Int? = null,
    val exceptionClass: String? = null,
    val message: String? = null,
    val cloudflare: String? = null,
)

data class ReaderDiagnosticPage(
    val index: Int,
    val state: String,
    val progress: Int,
    val host: String? = null,
    val error: String? = null,
)

fun pageStateLabel(state: Page.State): String = when (state) {
    Page.State.Queue -> "Queue"
    Page.State.LoadPage -> "LoadPage"
    Page.State.DownloadImage -> "DownloadImage"
    Page.State.Ready -> "Ready"
    is Page.State.Error -> "Error"
}

fun ReaderDiagnosticEvent.asLine(): String = buildString {
    append(formatDiagnosticTime(timeMillis))
    append(" page=")
    append(pageIndex?.let { it + 1 } ?: "-")
    append(" state=")
    append(state ?: "-")
    append(" host=")
    append(host ?: "-")
    if (httpStatus != null) {
        append(" http=")
        append(httpStatus)
    }
    if (!exceptionClass.isNullOrBlank()) {
        append(' ')
        append(exceptionClass)
    }
    if (!message.isNullOrBlank()) {
        append(": ")
        append(message)
    }
    if (!cloudflare.isNullOrBlank()) {
        append(" cloudflare=")
        append(cloudflare)
    }
    if (!chapterUrl.isNullOrBlank()) {
        append(" chapter=")
        append(chapterUrl)
    }
}

/** The text behind the reader's Reader diagnostics dialog and Copy details. */
fun readerDiagnosticsText(
    mangaTitle: String,
    chapterName: String,
    pages: List<ReaderDiagnosticPage>,
    events: List<String>,
): String = buildString {
    appendLine("KodaStream reader diagnostics")
    appendLine("Manga: $mangaTitle")
    appendLine("Chapter: $chapterName")
    appendLine("Pages: ${pages.size}")
    appendLine()
    pages.forEach { page ->
        append(page.index + 1)
        append(". ")
        append(page.state)
        append(" progress=")
        append(page.progress)
        append(" host=")
        append(page.host ?: "-")
        page.error?.takeIf { it.isNotBlank() }?.let {
            append(" error=")
            append(it)
        }
        appendLine()
    }
    appendLine()
    appendLine("Events (oldest first):")
    if (events.isEmpty()) {
        appendLine("none")
    } else {
        events.forEach { appendLine(it) }
    }
}

private fun formatDiagnosticTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(millis))

/**
 * Bounded, in-memory, local-only reader log. The reader menu shows it and the
 * crash recorder copies its newest entries into the report.
 */
object ReaderDiagnostics {
    const val LIMIT = 200

    private val events = ArrayDeque<ReaderDiagnosticEvent>()

    @Synchronized
    fun record(event: ReaderDiagnosticEvent) {
        if (events.size >= LIMIT) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun recent(limit: Int = 60): List<ReaderDiagnosticEvent> =
        events.takeLast(limit.coerceAtLeast(0))

    @Synchronized
    fun snapshot(): List<ReaderDiagnosticEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
