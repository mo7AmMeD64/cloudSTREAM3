package com.kodastream.app.manga.reader

import com.kodastream.app.manga.MangaChapter
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Section 4b: the pure decision and the one loop that uses it. */
class ReaderPageWatchdogTest {

    @Test
    fun nothingIsDoneForFinishedPages() {
        for (hasLoader in listOf(false, true)) {
            assertEquals(
                PageWatchdogAction.None,
                pageWatchdogAction(Page.State.Ready, 100, 999_999, hasLoader),
            )
            assertEquals(
                PageWatchdogAction.None,
                pageWatchdogAction(Page.State.Error(IOException("boom")), 0, 999_999, hasLoader),
            )
        }
    }

    @Test
    fun aQueuedPageReloadsAfterThreeSeconds() {
        assertEquals(
            PageWatchdogAction.None,
            pageWatchdogAction(Page.State.Queue, 0, 2_999, hasLoader = true),
        )
        assertEquals(
            PageWatchdogAction.Reload,
            pageWatchdogAction(Page.State.Queue, 0, 3_000, hasLoader = true),
        )
    }

    @Test
    fun aPageInFlightTimesOutAfterFortyFiveSeconds() {
        for (state in listOf(Page.State.LoadPage, Page.State.DownloadImage)) {
            assertEquals(
                PageWatchdogAction.None,
                pageWatchdogAction(state, 40, 44_999, hasLoader = true),
            )
            assertEquals(
                PageWatchdogAction.Timeout,
                pageWatchdogAction(state, 40, 45_000, hasLoader = true),
            )
        }
    }

    @Test
    fun aMissingLoaderAlwaysReloadsTheChapterInsteadOfTimingOut() {
        for (state in listOf(Page.State.Queue, Page.State.LoadPage, Page.State.DownloadImage)) {
            assertEquals(
                PageWatchdogAction.None,
                pageWatchdogAction(state, 0, 2_999, hasLoader = false),
            )
            assertEquals(
                PageWatchdogAction.Reload,
                pageWatchdogAction(state, 0, 3_000, hasLoader = false),
            )
        }
    }

    @Test
    fun aStalledDownloadBecomesAVisibleTimeout() = runBlocking {
        val page = newPage()
        page.status = Page.State.DownloadImage
        val loader = RecordingLoader()
        page.chapter.pageLoader = loader

        ReaderPageWatchdog(
            timeoutMillis = 150,
            tickMillis = 10,
        ).run(page)

        val error = page.status as? Page.State.Error ?: error("expected a timeout error")
        assertTrue(error.error.message.orEmpty().contains("timed out"))
        assertEquals(1, loader.aborted.size)
        assertEquals(page, loader.aborted.single().first)
    }

    @Test
    fun aMissingLoaderAsksForTheChapterToBeReloaded() = runBlocking {
        val page = newPage()
        page.status = Page.State.Queue
        var reloads = 0

        withTimeoutOrNull(1_000) {
            ReaderPageWatchdog(
                queueReloadMillis = 50,
                timeoutMillis = 10_000,
                tickMillis = 10,
            ).run(page) { reloads++ }
        }

        assertTrue("expected at least one reload, got $reloads", reloads >= 1)
    }

    private fun newPage(): ReaderPage {
        val chapter = ReaderChapter(MangaChapter(url = "/chapter"))
        return ReaderPage(0, url = "/p/0", imageUrl = "https://cdn.example.com/p/0").also {
            it.chapter = chapter
        }
    }

    private class RecordingLoader : PageLoader() {
        override var isLocal: Boolean = false
        val aborted = mutableListOf<Pair<ReaderPage, Throwable>>()

        override suspend fun getPages(): List<ReaderPage> = emptyList()

        override fun abortPage(page: ReaderPage, error: Throwable) {
            aborted += page to error
            super.abortPage(page, error)
        }
    }
}
