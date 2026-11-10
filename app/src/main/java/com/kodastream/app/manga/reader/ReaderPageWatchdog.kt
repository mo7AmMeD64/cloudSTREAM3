package com.kodastream.app.manga.reader

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeoutException

const val PAGE_QUEUE_RELOAD_MS = 3_000L
const val PAGE_TIMEOUT_MS = 45_000L
const val PAGE_WATCHDOG_TICK_MS = 500L

enum class PageWatchdogAction { None, Reload, Timeout }

/**
 * The watchdog's whole decision, kept pure so every state can be tested.
 * [progress] is part of the signature because a moving download is alive; the
 * caller folds progress changes into [sinceChange].
 */
fun pageWatchdogAction(
    state: Page.State,
    progress: Int,
    sinceChange: Long,
    hasLoader: Boolean,
    queueReloadMillis: Long = PAGE_QUEUE_RELOAD_MS,
    timeoutMillis: Long = PAGE_TIMEOUT_MS,
): PageWatchdogAction {
    if (state is Page.State.Ready || state is Page.State.Error) return PageWatchdogAction.None
    if (!hasLoader) {
        return if (sinceChange >= queueReloadMillis) PageWatchdogAction.Reload
        else PageWatchdogAction.None
    }
    if (sinceChange >= timeoutMillis) return PageWatchdogAction.Timeout
    if (state is Page.State.Queue && sinceChange >= queueReloadMillis) {
        return PageWatchdogAction.Reload
    }
    return PageWatchdogAction.None
}

fun pageTimeoutError(
    page: ReaderPage,
    state: Page.State,
    timeoutMillis: Long = PAGE_TIMEOUT_MS,
): TimeoutException {
    val host = page.imageUrl?.toHttpUrlOrNull()?.host ?: "unknown host"
    return TimeoutException(
        "Page ${page.number} timed out after ${timeoutMillis / 1000} s on " +
            "$host while ${pageStateLabel(state)}",
    )
}

/**
 * Watches one displayed page: relaunches a page stuck in Queue, recreates a
 * missing chapter loader, and turns a silent stall into a visible timeout.
 */
class ReaderPageWatchdog(
    private val queueReloadMillis: Long = PAGE_QUEUE_RELOAD_MS,
    private val timeoutMillis: Long = PAGE_TIMEOUT_MS,
    private val tickMillis: Long = PAGE_WATCHDOG_TICK_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    suspend fun run(page: ReaderPage, onReloadChapter: () -> Unit = {}) {
        var lastState = page.status
        var lastProgress = page.progress
        var changedAt = clock()
        var lastReloadAt = Long.MIN_VALUE / 2

        while (true) {
            delay(tickMillis)
            val state = page.status
            val progress = page.progress
            if (state is Page.State.Ready || state is Page.State.Error) return
            if (state != lastState || progress != lastProgress) {
                lastState = state
                lastProgress = progress
                changedAt = clock()
            }
            val sinceChange = clock() - changedAt
            val action = pageWatchdogAction(
                state = state,
                progress = progress,
                sinceChange = sinceChange,
                hasLoader = page.chapter.pageLoader != null,
                queueReloadMillis = queueReloadMillis,
                timeoutMillis = timeoutMillis,
            )
            when (action) {
                PageWatchdogAction.None -> Unit
                PageWatchdogAction.Reload -> {
                    val now = clock()
                    if (now - lastReloadAt < queueReloadMillis) continue
                    lastReloadAt = now
                    val loader = page.chapter.pageLoader
                    if (loader == null) onReloadChapter() else loader.loadPage(page)
                }
                PageWatchdogAction.Timeout -> {
                    val error = pageTimeoutError(page, state, timeoutMillis)
                    val loader = page.chapter.pageLoader
                    if (loader == null) page.status = Page.State.Error(error)
                    else loader.abortPage(page, error)
                    return
                }
            }
        }
    }
}
