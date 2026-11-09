package com.kodastream.app.manga.reader

/**
 * Ported from Mihon's `viewer/pager/PagerPageTracker.kt`. The item a pager is
 * on, shared by the pager view and the Compose viewers are kept out of scope.
 * It tells [host] of each new page, preloads the chapters around it, and
 * hands chapters set while the pager is moving to [setChaptersNow] to apply
 * once it's idle, so the pager never jumps mid-swipe.
 */
class PagerPageTracker(
    private val host: ReaderViewerHost,
    private val items: PagerItems,
    private val setChaptersNow: (ViewerChapters) -> Unit = {},
    private val onPageHolderSelected: (page: ReaderPage, forward: Boolean) -> Unit = { _, _ -> },
) {

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    var currentPage: Any? = null
        private set

    /**
     * Viewer chapters to set when the pager becomes idle. Setting them during
     * a drag or a settle would be a noticeable jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the pager is in idle mode. Setting this to true applies the
     * awaiting chapters, if any, and preloads in the last-one-page case.
     */
    var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersNow(viewerChapters)
                    awaitingIdleViewerChapters = null
                    if (viewerChapters.currChapter.pages?.size == 1) {
                        items.nextTransition?.to?.let(host::requestPreloadChapter)
                    }
                }
            }
        }

    /**
     * Sets the given [chapters] now if the pager is idle, otherwise once it becomes idle.
     */
    fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersNow(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Called with the adapter position of the newly selected item.
     */
    fun onPageChange(position: Int) {
        val page = items.items.getOrNull(position)
        if (page != null && currentPage != page) {
            val allowPreload = checkAllowPreload(page as? ReaderPage)
            val forward = when {
                currentPage is ReaderPage && page is ReaderPage ->
                    // Same number means a split page; the InsertPage is the second half.
                    if (page.number == (currentPage as ReaderPage).number) {
                        page is InsertPage
                    } else {
                        page.number > (currentPage as ReaderPage).number
                    }
                currentPage is ChapterTransition.Prev && page is ReaderPage ->
                    false
                else -> true
            }
            currentPage = page
            when (page) {
                is ReaderPage -> onReaderPageSelected(page, allowPreload, forward)
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // No page to check (a transition was left): preload is allowed.
        page ?: return true

        // Initial opening: preload is allowed.
        currentPage ?: return true

        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            (currentPage as? ReaderPage)?.chapter -> true
            items.nextTransition?.to -> true
            else -> false
        }
    }

    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        host.onPageSelected(page)

        onPageHolderSelected(page, forward)

        // A split half must not trigger a preload; it would jump the pager.
        if (page is InsertPage) return

        // Preload next chapter once we're within the last 5 pages of the current chapter.
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == items.currentChapter) {
            items.nextTransition?.to?.let(host::requestPreloadChapter)
        }
    }

    private fun onTransitionSelected(transition: ChapterTransition) {
        val toChapter = transition.to
        if (toChapter != null) {
            host.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters: open the menu, since the user is probably leaving the reader.
            host.showMenu()
        }
    }
}
