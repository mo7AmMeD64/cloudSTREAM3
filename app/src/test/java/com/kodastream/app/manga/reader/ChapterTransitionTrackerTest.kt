package com.kodastream.app.manga.reader

import com.kodastream.app.manga.MangaChapter
import eu.kanade.tachiyomi.source.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTransitionTrackerTest {

    private val prevChapter = chapterWithPages("A", 1f)
    private val currChapter = chapterWithPages("B", 2f)
    private val viewerChapters = ViewerChapters(currChapter, prevChapter, null)

    private fun chapterWithPages(url: String, number: Float): ReaderChapter =
        ReaderChapter(
            MangaChapter(
                url = url,
                name = "Chapter $url",
                number = number,
                sourceOrder = number.toInt(),
            ),
        ).also { chapter ->
            chapter.state = ReaderChapter.State.Loaded(
                listOf(ReaderPage(0, "p0-$url", "img-$url").also { it.chapter = chapter }),
            )
        }

    private class FakeHost : ReaderViewerHost {
        override val settings = com.kodastream.app.manga.MangaReaderSettings()
        override val sourceId: Long = 1L
        override val readerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        var preloaded = mutableListOf<ReaderChapter>()
        var selected = mutableListOf<ReaderPage>()
        var showMenuCalls = 0
        var hideMenuCalls = 0

        override fun onPageSelected(page: ReaderPage) { selected += page }
        override fun onTransitionSelected(transition: ChapterTransition) = Unit
        override fun onTap(xFraction: Float, yFraction: Float) = Unit
        override fun onLongTap(page: ReaderPage) = Unit
        override fun requestPreloadChapter(chapter: ReaderChapter) { preloaded += chapter }
        override fun reloadChapter(chapter: ReaderChapter) = Unit
        override fun openInWebView() = Unit
        override fun showMenu() { showMenuCalls++ }
        override fun hideMenu() { hideMenuCalls++ }
    }

    @Test
    fun reachingTheNextTransitionJumpsToTheDestinationChapter() {
        val host = FakeHost()
        val items = PagerItems(isReversed = false).also {
            it.setChapters(viewerChapters, forceTransition = true, currentPosition = 0)
        }
        val tracker = PagerPageTracker(host, items) { _, _ -> }

        val nextIndex = items.items.indexOfFirst { it is ChapterTransition.Next }
        assertTrue("the next transition is in the list", nextIndex > 0)
        tracker.onPageChange(nextIndex)

        assertEquals(emptyList<ReaderChapter>(), host.preloaded)
        // No next chapter exists: the reader opens the menu, like Mihon does.
        assertEquals(1, host.showMenuCalls)
    }

    @Test
    fun scrollingUpToThePreviousTransitionPreloadsIt() {
        val host = FakeHost()
        val items = WebtoonItems().also {
            it.setChapters(viewerChapters, forceTransition = true)
        }
        val tracker = WebtoonPageTracker(host, items, hideThreshold = 31)

        val prevIndex = items.items.indexOfFirst { it is ChapterTransition.Prev }
        assertTrue("the previous transition is in the list", prevIndex >= 0)
        tracker.onScrolledBy(dy = -10f, firstVisible = prevIndex, lastEndVisible = prevIndex)

        assertEquals(listOf(prevChapter), host.preloaded)

        tracker.onScrolledBy(dy = 40f, firstVisible = prevIndex, lastEndVisible = prevIndex)
        assertEquals(1, host.hideMenuCalls)
    }

    @Test
    fun turningIntoTheNextChapterReportsPageSelected() {
        val host = FakeHost()
        val items = PagerItems(isReversed = false).also {
            it.setChapters(viewerChapters, forceTransition = true, currentPosition = 0)
        }
        val tracker = PagerPageTracker(host, items) { _, _ -> }

        val currPageIndex = items.items.indexOfFirst { it is ReaderPage && it.chapter === currChapter }
        assertTrue(currPageIndex >= 0)
        tracker.onPageChange(currPageIndex)
        assertEquals(currChapter, host.selected.firstOrNull()?.chapter)
    }
}
