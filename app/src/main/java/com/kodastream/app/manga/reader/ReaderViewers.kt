package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.kodastream.app.R
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.MangaReadingMode
import com.kodastream.app.manga.MangaScaleType
import com.kodastream.app.manga.MangaZoomStart
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource

/** Everything a viewer needs from the activity/view model. */
interface ReaderViewerHost {
    val settings: MangaReaderSettings
    val sourceId: Long
    val readerScope: CoroutineScope

    fun onPageSelected(page: ReaderPage)
    fun onTransitionSelected(transition: ChapterTransition)
    fun onTap(xFraction: Float, yFraction: Float)
    fun onLongTap(page: ReaderPage)
    fun requestPreloadChapter(chapter: ReaderChapter)
    fun reloadChapter(chapter: ReaderChapter)
    fun openInWebView()

    /** Mihon's ReaderActivity.showMenu; the webtoon tracker uses both of these. */
    fun showMenu()
    fun hideMenu()
}

interface ReaderViewer {
    val view: View
    fun setChapters(chapters: ViewerChapters)
    fun moveToPage(page: ReaderPage)
    fun moveForward()
    fun moveBackward()
    fun destroy()
}

/**
 * Ported from Mihon's `viewer/pager/PagerItems.kt`: the pager's items, flipped
 * for right-to-left reading, with [InsertPage] support for split wide pages.
 */
class PagerItems(private val isReversed: Boolean) {

    var items: List<Any> = emptyList()
        private set

    /** Pages split while their chapter was only an adjacent one. */
    private var preprocessed: List<InsertPage> = emptyList()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null
        private set

    fun setChapters(
        chapters: ViewerChapters,
        forceTransition: Boolean,
        currentPosition: Int,
    ): Update {
        var insertPageLastPage: InsertPage? = null

        val currPages = chapters.currChapter.pages?.toMutableList()?.also { pages ->
            val lastPage = pages.lastOrNull() ?: return@also
            preprocessed
                .filter { it.chapter.chapter.url == chapters.currChapter.chapter.url }
                .sortedByDescending { it.index }
                .forEach { page ->
                    if (lastPage.index == page.index) {
                        insertPageLastPage = page
                    }
                    pages.add((page.index + 1).coerceIn(0, pages.size), page)
                }
        }

        val newItems = viewerItems(chapters, forceTransition, currPages)
        currentChapter = chapters.currChapter
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)

        val currentItem = items.getOrNull(currentPosition)

        if (isReversed) {
            newItems.reverse()
        }

        val adjacentChapterUrls = setOfNotNull(
            chapters.prevChapter?.chapter?.url,
            chapters.nextChapter?.chapter?.url,
        )
        preprocessed = preprocessed.filter { it.chapter.chapter.url in adjacentChapterUrls }
        items = newItems

        // Before the pager has laid anything out it would keep its position
        // while pages are added in front, landing in another chapter.
        val position = items.indexOf(currentItem).takeIf { it != -1 && it != currentPosition }
        return Update(position, insertPageLastPage)
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage): Boolean {
        if (currentPage !is ReaderPage) return false

        val currentIndex = items.indexOf(currentPage)
        if (currentIndex == -1) return false

        // Keep pages of an adjacent chapter for when it becomes current.
        if (currentPage.chapter.chapter.url != currentChapter?.chapter?.url) {
            val url = newPage.chapter.chapter.url
            if (preprocessed.none { it.chapter.chapter.url == url && it.index == newPage.index }) {
                preprocessed = preprocessed + newPage
            }
            return false
        }

        val placeAtIndex = if (isReversed) currentIndex else currentIndex + 1
        if (placeAtIndex !in 0..items.size) return false

        if (isReversed &&
            placeAtIndex - 1 >= 0 &&
            items[placeAtIndex - 1] is InsertPage
        ) {
            return false
        }
        if (placeAtIndex < items.size && items[placeAtIndex] is InsertPage) {
            return false
        }

        items = items.toMutableList().apply { add(placeAtIndex, newPage) }
        return true
    }

    fun cleanupPageSplit() {
        items = items.filterNot { it is InsertPage }
    }

    /**
     * Where the pager goes after the items change: [position] keeps the same
     * item, [insertPage] is the split second half at the chapter's end.
     */
    class Update(val position: Int?, val insertPage: InsertPage?)
}

/**
 * Ported from Mihon's pager viewers. Right-to-left reading reverses the item
 * list; forward always means the next item, exactly as Mihon does it.
 */
class PagerViewer(
    private val context: Context,
    private val host: ReaderViewerHost,
    private val readingRtl: Boolean,
    private val isHorizontal: Boolean,
) : ReaderViewer {

    private val pager = Pager(context, isHorizontal).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        offscreenPageLimit = 1
        // Don't lay the pager out before the first chapters arrive.
        visibility = View.GONE
    }
    private val items = PagerItems(isReversed = readingRtl)
    private val adapter = ReaderPagerAdapter()
    private val dualPage = host.settings.asDualPageSettings()
    private val tracker = PagerPageTracker(host, items, ::setChaptersNow) { page, forward ->
        getPageHolder(page)?.onPageSelected(forward)
    }

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            tracker.onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            tracker.isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    override val view: View get() = pager

    init {
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = { event ->
            host.onTap(
                event.x / pager.width.coerceAtLeast(1),
                event.y / pager.height.coerceAtLeast(1),
            )
        }
        pager.longTapListener = {
            if (!host.settings.longTapEnabled) {
                false
            } else {
                val page = adapter.items.getOrNull(pager.currentItem) as? ReaderPage
                if (page != null) {
                    host.onLongTap(page)
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun setChapters(chapters: ViewerChapters) {
        tracker.setChapters(chapters)
    }

    /**
     * Ported from Mihon's `setChaptersInternal`: the listener is removed while
     * the items change, the pager follows the item still on screen, and the
     * first layout lands on the chapter's requested page.
     */
    private fun setChaptersNow(chapters: ViewerChapters) {
        pager.removeOnPageChangeListener(pagerListener)
        try {
            val forceTransition = host.settings.alwaysShowChapterTransition ||
                adapter.items.getOrNull(pager.currentItem) is ChapterTransition
            val update = items.setChapters(chapters, forceTransition, pager.currentItem)
            adapter.notifyDataSetChanged()

            update.position?.let { pager.setCurrentItem(it, false) }
            update.insertPage?.let(::moveToPage)

            if (pager.visibility != View.VISIBLE) {
                val pages = chapters.currChapter.pages
                if (!pages.isNullOrEmpty()) {
                    moveToPage(pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)])
                }
                pager.visibility = View.VISIBLE
            }
        } finally {
            pager.addOnPageChangeListener(pagerListener)
        }
        // Setting currentItem doesn't trigger the listener in every case.
        tracker.onPageChange(pager.currentItem)
    }

    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position == -1) return
        val current = pager.currentItem
        pager.setCurrentItem(position, true)
        if (current == position) {
            tracker.onPageChange(position)
        }
    }

    override fun moveForward() = if (readingRtl) moveLeft() else moveRight()

    override fun moveBackward() = if (readingRtl) moveRight() else moveLeft()

    private fun moveRight() {
        if (pager.currentItem == adapter.count - 1) return
        val holder = (tracker.currentPage as? ReaderPage)?.let(::getPageHolder)
        if (holder != null && host.settings.navigateToPan && holder.canPanRight()) {
            holder.panRight()
        } else {
            pager.setCurrentItem(pager.currentItem + 1, host.settings.pageTransitions)
        }
    }

    private fun moveLeft() {
        if (pager.currentItem == 0) return
        val holder = (tracker.currentPage as? ReaderPage)?.let(::getPageHolder)
        if (holder != null && host.settings.navigateToPan && holder.canPanLeft()) {
            holder.panLeft()
        } else {
            pager.setCurrentItem(pager.currentItem - 1, host.settings.pageTransitions)
        }
    }

    override fun destroy() {
        pager.removeOnPageChangeListener(pagerListener)
        pager.adapter = null
    }

    internal fun processPagedImage(page: ReaderPage, source: BufferedSource): BufferedSource =
        dualPage.processPagedImage(
            page = page,
            imageSource = source,
            isLeftToRight = !readingRtl,
            onPageSplit = { onPageSplit(page, it) },
        )

    internal fun configForPage(): ReaderPageImageView.Config {
        val settings = host.settings
        return ReaderPageImageView.Config(
            zoomDuration = if (settings.pageTransitions) 200 else 0,
            minimumScaleType = settings.scaleType.minimumScaleType(),
            cropBorders = settings.cropBorders,
            zoomStartPosition = zoomStartPosition(),
            landscapeZoom = settings.landscapeZoom,
        )
    }

    private fun zoomStartPosition(): ReaderPageImageView.ZoomStartPosition =
        when (host.settings.zoomStart) {
            MangaZoomStart.LEFT -> ReaderPageImageView.ZoomStartPosition.LEFT
            MangaZoomStart.CENTER -> ReaderPageImageView.ZoomStartPosition.CENTER
            MangaZoomStart.RIGHT -> ReaderPageImageView.ZoomStartPosition.RIGHT
            MangaZoomStart.AUTOMATIC -> when {
                readingRtl -> ReaderPageImageView.ZoomStartPosition.RIGHT
                !isHorizontal -> ReaderPageImageView.ZoomStartPosition.CENTER
                else -> ReaderPageImageView.ZoomStartPosition.LEFT
            }
        }

    internal fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        // Insert on the UI thread or the images go blank.
        pager.post { adapter.onPageSplit(currentPage, newPage) }
    }

    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children.filterIsInstance<PagerPageHolder>().firstOrNull { it.page === page }

    private inner class ReaderPagerAdapter : PagerAdapter() {
        val items: List<Any> get() = this@PagerViewer.items.items

        override fun getCount(): Int = items.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val item = items[position]
            val view: View = when (item) {
                is ReaderPage -> PagerPageHolder(context, this@PagerViewer, host, item)
                is ChapterTransition -> PagerTransitionHolder(context, item, host)
                else -> error("Unknown reader item")
            }
            view.tag = item
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            val view = obj as? View ?: return
            if (view is PagerPageHolder) view.recycle()
            container.removeView(view)
        }

        override fun getItemPosition(obj: Any): Int {
            val item = (obj as? View)?.tag
            return item?.let { items.indexOf(it) }?.takeIf { it != -1 }
                ?: PagerAdapter.POSITION_NONE
        }

        override fun getPageTitle(position: Int): CharSequence? = null

        fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
            if (this@PagerViewer.items.onPageSplit(currentPage, newPage)) {
                notifyDataSetChanged()
            }
        }
    }
}

/**
 * Shared page progress, error panel and watchdog. It owns the loader retry path
 * Mihon keeps in each holder, so the two viewers cannot drift apart.
 */
private class ReaderPageState(
    container: FrameLayout,
    private val host: ReaderViewerHost,
    private val onReady: (ReaderPage) -> Unit,
) {
    private val progress = ProgressBar(container.context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
    }
    private val error = PageErrorPanel(container.context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
    }
    private var job: Job? = null
    private var page: ReaderPage? = null

    init {
        container.addView(progress)
        container.addView(error)
    }

    fun bind(page: ReaderPage) {
        this.page = page
        job?.cancel()
        progress.isVisible = true
        progress.isIndeterminate = true
        error.hide()
        job = host.readerScope.launch {
            supervisorScope {
                launch(Dispatchers.IO) { attemptLoad(page) }
                launch { page.statusFlow.collectLatest { state -> render(page, state) } }
                launch {
                    page.progressFlow.collect { value ->
                        if (page === this@ReaderPageState.page &&
                            page.status is Page.State.DownloadImage
                        ) {
                            progress.isVisible = true
                            error.hide()
                            progress.isIndeterminate = value < 0
                            if (value >= 0) progress.progress = value
                        }
                    }
                }
                launch { ReaderPageWatchdog().run(page) { host.reloadChapter(page.chapter) } }
            }
        }
    }

    fun onImageLoaded() {
        progress.isVisible = false
        error.hide()
    }

    fun onImageError(e: Throwable?) {
        progress.isVisible = false
        error.show(
            e,
            onRetry = { retry() },
            onOpenWebView = host::openInWebView,
        )
    }

    fun recycle() {
        job?.cancel()
        job = null
        page = null
    }

    private suspend fun attemptLoad(page: ReaderPage) {
        if (page.status is Page.State.Ready) return
        val loader = page.chapter.pageLoader
        if (loader == null) {
            host.reloadChapter(page.chapter)
        } else {
            runCatching { loader.loadPage(page) }
        }
    }

    private fun render(page: ReaderPage, state: Page.State) {
        if (page !== this.page) return
        when (state) {
            Page.State.Queue, Page.State.LoadPage -> showLoading()
            Page.State.DownloadImage -> {
                showLoading()
                progress.isIndeterminate = page.progress < 0
                if (page.progress >= 0) progress.progress = page.progress
            }
            Page.State.Ready -> {
                progress.isVisible = false
                error.hide()
                onReady(page)
            }
            is Page.State.Error -> {
                progress.isVisible = false
                error.show(
                    state.error,
                    onRetry = { retry() },
                    onOpenWebView = host::openInWebView,
                )
            }
        }
    }

    private fun retry() {
        val page = this.page ?: return
        page.status = Page.State.Queue
        val loader = page.chapter.pageLoader
        if (loader == null) host.reloadChapter(page.chapter) else loader.retryPage(page)
    }

    private fun showLoading() {
        progress.isVisible = true
        progress.isIndeterminate = true
        progress.bringToFront()
        error.hide()
    }
}

/** Message plus Retry and, for a Cloudflare-style block, Open in WebView. */
private class PageErrorPanel(context: Context) : LinearLayout(context) {
    private val message = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 15f
    }
    private val retry = android.widget.Button(context).apply {
        setText(R.string.manga_reader_retry)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }
    }
    private val webView = android.widget.Button(context).apply {
        setText(R.string.manga_reader_open_webview)
        isVisible = false
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        isVisible = false
        addView(message)
        addView(retry)
        addView(webView)
    }

    fun show(error: Throwable?, onRetry: () -> Unit, onOpenWebView: () -> Unit) {
        message.text = error?.message ?: context.getString(R.string.manga_reader_page_failed)
        retry.setOnClickListener { onRetry() }
        val code = httpErrorCode(error)
        webView.isVisible = code == 403 || code == 503
        webView.setOnClickListener { onOpenWebView() }
        bringToFront()
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}

private fun httpErrorCode(error: Throwable?): Int? {
    var current = error
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is HttpException) return current.code
        current = current.cause
    }
    return null
}

/**
 * Pager page holder, ported from `PagerPageHolder.kt`. The buffer is split or
 * rotated off the main thread exactly where Mihon does it.
 */
private class PagerPageHolder(
    context: Context,
    private val viewer: PagerViewer,
    private val host: ReaderViewerHost,
    val page: ReaderPage,
) : ReaderPageImageView(context) {

    private val state = ReaderPageState(this, host) { ready -> loadImage(ready) }
    private var loadJob: Job? = null

    init {
        onImageLoaded = { state.onImageLoaded() }
        onImageLoadError = { e -> state.onImageError(e) }
        onScaleChanged = { host.hideMenu() }
        state.bind(page)
    }

    private fun loadImage(page: ReaderPage) {
        val streamFn = page.stream ?: return
        loadJob?.cancel()
        loadJob = host.readerScope.launch {
            try {
                val buffer = withContext(Dispatchers.IO) {
                    streamFn().use { Buffer().readFrom(it) }
                }
                val (source, isAnimated) = withContext(Dispatchers.Default) {
                    val source = viewer.processPagedImage(page, buffer)
                    source to ReaderImageUtil.isAnimatedAndSupported(source)
                }
                setImage(page, source, isAnimated, viewer.configForPage())
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                state.onImageError(e)
            }
        }
    }

    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        state.recycle()
        super.recycle()
    }
}

/** Pager chapter transition holder, ported from `PagerTransitionHolder.kt`. */
private class PagerTransitionHolder(
    context: Context,
    transition: ChapterTransition,
    host: ReaderViewerHost,
) : FrameLayout(context) {
    init {
        setPadding(dp(32), 0, dp(32), 0)
        addView(
            ReaderTransitionView(context, host.settings).apply {
                bind(transition, host)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            },
        )
    }
}

/**
 * Webtoon viewer, ported from `WebtoonViewer.kt` and `WebtoonAdapter.kt`.
 * Pages are added with margins and a placeholder height so the list cannot
 * recycle a page before its first strip is decoded.
 */
class WebtoonViewer(
    context: Context,
    private val host: ReaderViewerHost,
) : ReaderViewer {

    val isContinuous: Boolean = host.settings.mode != MangaReadingMode.WEBTOON_GAPS

    val sidePaddingPixels: Int get() = recycler.sidePaddingPixels

    private val recycler = WebtoonRecyclerView(context)
    private val frame = WebtoonFrame(context)
    private val scrollDistance = context.resources.displayMetrics.heightPixels * 3 / 4
    private val layoutManager = androidx.recyclerview.widget.WebtoonLayoutManager(context, scrollDistance)
    private val webtoonItems = WebtoonItems()
    private val adapter = WebtoonAdapter()
    private val tracker = WebtoonPageTracker(host, webtoonItems, HIDE_MENU_DISTANCE)

    override val view: View get() = frame

    init {
        frame.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        frame.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        recycler.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.setItemViewCacheSize(4)
        recycler.sidePaddingPercent = host.settings.webtoonPadding
        recycler.visibility = View.GONE
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    tracker.onScrolled(layoutManager.findLastEndVisibleItemPosition())
                    tracker.onScrolledBy(
                        dy = dy.toFloat(),
                        firstVisible = layoutManager.findFirstVisibleItemPosition(),
                        lastEndVisible = layoutManager.findLastEndVisibleItemPosition(),
                    )
                }
            },
        )
        recycler.tapListener = { event ->
            host.onTap(
                event.x / recycler.width.coerceAtLeast(1),
                event.y / recycler.originalHeight.coerceAtLeast(1),
            )
        }
        recycler.longTapListener = { event ->
            if (!host.settings.longTapEnabled) {
                false
            } else {
                val child = recycler.findChildViewUnder(event.x, event.y)
                val page = child?.let {
                    webtoonItems.items.getOrNull(recycler.getChildAdapterPosition(it))
                } as? ReaderPage
                if (page != null) {
                    host.onLongTap(page)
                    true
                } else {
                    false
                }
            }
        }
        frame.addView(recycler)
        // After the recycler is a child, so the frame's setter reaches it.
        frame.doubleTapZoom = host.settings.doubleTapZoom
    }

    override fun setChapters(chapters: ViewerChapters) {
        val old = webtoonItems.items
        // Keep the transition while the user is on it, or it would disappear
        // from under their finger the moment the chapter finishes loading.
        val forceTransition = host.settings.alwaysShowChapterTransition ||
            tracker.currentPage is ChapterTransition
        webtoonItems.setChapters(chapters, forceTransition)
        val items = webtoonItems.items
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == items[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = true
        }).dispatchUpdatesTo(adapter)

        if (recycler.visibility != View.VISIBLE) {
            val pages = chapters.currChapter.pages
            if (!pages.isNullOrEmpty()) {
                moveToPage(pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)])
            }
            recycler.visibility = View.VISIBLE
        }
    }

    override fun moveToPage(page: ReaderPage) {
        val position = webtoonItems.items.indexOf(page)
        if (position < 0) return
        layoutManager.scrollToPositionWithOffset(position, 0)
        if (layoutManager.findLastEndVisibleItemPosition() == -1) {
            tracker.onScrolled(position)
        }
    }

    override fun moveForward() {
        if (host.settings.pageTransitions) recycler.smoothScrollBy(0, scrollDistance)
        else recycler.scrollBy(0, scrollDistance)
    }

    override fun moveBackward() {
        if (host.settings.pageTransitions) recycler.smoothScrollBy(0, -scrollDistance)
        else recycler.scrollBy(0, -scrollDistance)
    }

    override fun destroy() {
        recycler.adapter = null
        frame.removeAllViews()
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun onPageViewCreated(view: ReaderPageImageView) = Unit

    private inner class WebtoonAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = webtoonItems.items.size

        override fun getItemViewType(position: Int): Int =
            if (webtoonItems.items[position] is ReaderPage) PAGE_VIEW else TRANSITION_VIEW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == PAGE_VIEW) {
                WebtoonPageHolder(
                    ReaderPageImageView(parent.context, isWebtoon = true),
                    this@WebtoonViewer,
                    host,
                )
            } else {
                WebtoonTransitionHolder(parent.context, host)
            }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is WebtoonPageHolder -> holder.bind(webtoonItems.items[position] as ReaderPage)
                is WebtoonTransitionHolder -> holder.bind(webtoonItems.items[position] as ChapterTransition)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? WebtoonPageHolder)?.recycle()
        }
    }
}

// Mihon's LOW hide threshold; scrolling more than this hides the bars.
private const val HIDE_MENU_DISTANCE = 31

private const val PAGE_VIEW = 0
private const val TRANSITION_VIEW = 1

/**
 * Webtoon page holder, ported from `WebtoonPageHolder.kt`. The frame's minimum
 * height keeps the item measured while the first strip decodes, which is what
 * stops a long chapter from showing blank rows.
 */
private class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    private val viewer: WebtoonViewer,
    private val host: ReaderViewerHost,
) : RecyclerView.ViewHolder(frame) {

    private val state = ReaderPageState(frame, host) { page -> setImage(page) }

    init {
        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { e -> state.onImageError(e) }
        frame.onScaleChanged = { host.hideMenu() }
        refreshLayoutParams()
    }

    fun bind(page: ReaderPage) {
        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { e -> state.onImageError(e) }
        refreshLayoutParams()
        frame.minimumHeight = parentHeight()
        state.bind(page)
    }

    private fun setImage(page: ReaderPage) {
        val file = page.file ?: return
        frame.setImage(
            file,
            page.animated,
            ReaderPageImageView.Config(
                zoomDuration = if (host.settings.pageTransitions) 200 else 0,
                minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                cropBorders = host.settings.cropBorders,
            ),
        )
    }

    private fun onImageDecoded() {
        frame.minimumHeight = 0
        state.onImageLoaded()
    }

    private fun refreshLayoutParams() {
        val params = frame.layoutParams as? RecyclerView.LayoutParams
            ?: RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.marginStart = viewer.sidePaddingPixels
        params.marginEnd = viewer.sidePaddingPixels
        params.bottomMargin = if (viewer.isContinuous) 0 else dp(15)
        frame.layoutParams = params
    }

    private fun parentHeight(): Int =
        viewer.view.height.takeIf { it > 0 } ?: frame.resources.displayMetrics.heightPixels

    fun recycle() {
        state.recycle()
        frame.minimumHeight = 0
        frame.recycle()
    }
}

/** Webtoon transition holder, ported from `WebtoonTransitionHolder.kt`. */
private class WebtoonTransitionHolder(
    context: Context,
    private val host: ReaderViewerHost,
) : RecyclerView.ViewHolder(
    ReaderTransitionView(context, host.settings).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(dp(24), dp(96), dp(24), dp(96))
    },
) {
    private val transitionView = itemView as ReaderTransitionView

    fun bind(transition: ChapterTransition) {
        transitionView.bind(transition, host)
    }
}

/** Compose transition page, replacing Mihon's `ReaderTransitionView`. */
class ReaderTransitionView(
    context: Context,
    private val settings: MangaReaderSettings,
) : FrameLayout(context) {

    private val compose = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    init {
        addView(compose)
    }

    fun bind(transition: ChapterTransition, host: ReaderViewerHost) {
        compose.setContent {
            val dark = settings.theme != com.kodastream.app.manga.MangaReaderTheme.WHITE
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                ReaderTransitionContent(transition, host)
            }
        }
    }
}

@Composable
private fun ReaderTransitionContent(
    transition: ChapterTransition,
    host: ReaderViewerHost,
) {
    val from = transition.from.chapter
    val next = transition.to
    val to = next?.chapter
    val gap = chapterGap(transition.from, next)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = transitionLabel(transition, from.name, to?.name),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (gap > 0) {
            Text(
                text = pluralStringResource(R.plurals.manga_reader_missing_chapters, gap, gap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        when {
            next == null -> Text(
                text = stringResource(
                    if (transition is ChapterTransition.Next) R.string.manga_reader_no_next_chapter
                    else R.string.manga_reader_no_previous_chapter,
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            next.state is ReaderChapter.State.Error -> {
                Text(
                    text = (next.state as ReaderChapter.State.Error).error.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { next.let(host::requestPreloadChapter) }) {
                    Text(stringResource(R.string.manga_reader_retry))
                }
            }
            next.state !is ReaderChapter.State.Loaded -> Text(
                text = stringResource(R.string.manga_reader_loading_chapter),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun transitionLabel(transition: ChapterTransition, from: String, to: String?): String {
    val finished = if (transition is ChapterTransition.Next) {
        stringResource(R.string.manga_reader_finished_chapter, from)
    } else {
        stringResource(R.string.manga_reader_start_chapter, from)
    }
    if (to == null) return finished
    val other = stringResource(
        if (transition is ChapterTransition.Next) R.string.manga_reader_next_chapter_named
        else R.string.manga_reader_previous_chapter_named,
        to,
    )
    return finished + "\n" + other
}

private fun MangaReaderSettings.asDualPageSettings(): DualPageSettings = object : DualPageSettings {
    override val dualPageSplit: Boolean get() = this@asDualPageSettings.dualPageSplit
    override val dualPageInvert: Boolean get() = this@asDualPageSettings.dualPageInvert
    override val dualPageRotateToFit: Boolean get() = this@asDualPageSettings.dualPageRotateToFit
    override val dualPageRotateToFitInvert: Boolean get() = this@asDualPageSettings.dualPageRotateToFitInvert
}

private fun MangaScaleType.minimumScaleType(): Int = when (this) {
    MangaScaleType.FIT_SCREEN -> SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
    MangaScaleType.FIT_WIDTH -> SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH
    MangaScaleType.FIT_HEIGHT -> SubsamplingScaleImageView.SCALE_TYPE_FIT_HEIGHT
    MangaScaleType.ORIGINAL -> SubsamplingScaleImageView.SCALE_TYPE_ORIGINAL_SIZE
    MangaScaleType.SMART_FIT -> SubsamplingScaleImageView.SCALE_TYPE_SMART_FIT
}

private fun dp(value: Int): Int =
    (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
