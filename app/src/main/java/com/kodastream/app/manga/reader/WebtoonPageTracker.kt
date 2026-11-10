package com.kodastream.app.manga.reader

import kotlin.math.abs

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonPageTracker.kt`. The items are a
 * continuous list: when scrolling further than [hideThreshold] it hides the
 * menu, and it preloads the previous chapter as soon as its transition is on
 * screen.
 */
class WebtoonPageTracker(
    private val host: ReaderViewerHost,
    private val items: WebtoonItems,
    private val hideThreshold: Int,
) {

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    var currentPage: Any? = null
        private set

    /**
     * Marks the item at [position], the last one whose end is visible, as active.
     */
    fun onScrolled(position: Int) {
        val item = items.items.getOrNull(position)
        val allowPreload = checkAllowPreload(item as? ReaderPage)
        if (item != null && currentPage != item) {
            currentPage = item
            when (item) {
                is ReaderPage -> onPageSelected(item, allowPreload)
                is ChapterTransition -> onTransitionSelected(item)
            }
        }
    }

    /**
     * Called when the list scrolled by [dy] pixels, leaving items
     * [firstVisible] until [lastEndVisible] on screen.
     */
    fun onScrolledBy(dy: Float, firstVisible: Int, lastEndVisible: Int) {
        if (abs(dy) > hideThreshold) host.hideMenu()

        if (dy < 0) {
            val firstItem = items.items.getOrNull(firstVisible)
            if (firstItem is ChapterTransition.Prev && firstItem.to != null) {
                host.requestPreloadChapter(firstItem.to)
            }
        }

        val lastItem = items.items.getOrNull(lastEndVisible)
        if (lastItem is ChapterTransition.Next && lastItem.to == null) {
            host.showMenu()
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // No page to check (a transition was left): preload is allowed.
        page ?: return true

        // Initial opening: preload is allowed.
        currentPage ?: return true

        return when (page.chapter) {
            (currentPage as? ReaderPage)?.chapter -> true
            lastChapter() -> true
            else -> false
        }
    }

    private fun onPageSelected(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        host.onPageSelected(page)

        // Preload next chapter once we're within the last 5 pages of the current chapter.
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == items.currentChapter) {
            val transitionChapter = lastChapter()
            if (transitionChapter != null) {
                host.requestPreloadChapter(transitionChapter)
            }
        }
    }

    private fun onTransitionSelected(transition: ChapterTransition) {
        val toChapter = transition.to
        if (toChapter != null) {
            host.requestPreloadChapter(toChapter)
        }
    }

    /**
     * The chapter at the end of the list: the next transition's, or that of the next chapter's pages.
     */
    private fun lastChapter() = when (val lastItem = items.items.lastOrNull()) {
        is ChapterTransition.Next -> lastItem.to
        is ReaderPage -> lastItem.chapter
        else -> null
    }
}
