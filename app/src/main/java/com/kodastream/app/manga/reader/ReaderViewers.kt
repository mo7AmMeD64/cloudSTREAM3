package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
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
import com.kodastream.app.R
import com.kodastream.app.manga.MangaReaderSettings
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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

/** Ported from Mihon's `viewer/pager/PagerItems.kt`, without page splitting. */
class PagerItems(private val isReversed: Boolean) {
    var items: List<Any> = emptyList()
        private set
    var currentChapter: ReaderChapter? = null
        private set
    var nextTransition: ChapterTransition.Next? = null
        private set

    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean, currentPosition: Int): Int {
        val currentItem = items.getOrNull(currentPosition)
        currentChapter = chapters.currChapter
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
        val newItems = viewerItems(chapters, forceTransition).toMutableList()
        if (isReversed) newItems.reverse()
        items = newItems
        // Same item still in the list: follow it. Otherwise the pager goes to
        // the chapter's requested page.
        return newItems.indexOf(currentItem)
    }
}

/** Ported from Mihon's pager viewers; R2L reverses the item list. */
class PagerViewer(
    private val context: Context,
    private val host: ReaderViewerHost,
    private val readingRtl: Boolean,
    isHorizontal: Boolean,
) : ReaderViewer {

    private val pager = Pager(context, isHorizontal).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        offscreenPageLimit = 1
    }
    private val items = PagerItems(isReversed = readingRtl)
    private val adapter = ReaderPagerAdapter()

    /** Until the first layout, the pager must move to the chapter's requested page. */
    private var firstLayout = true

    private val pagerListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            tracker.onPageChange(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            tracker.isIdle = state == ViewPager.SCROLL_STATE_IDLE
        }
    }

    private val tracker = PagerPageTracker(host, items, ::setChaptersNow)

    override val view: View get() = pager

    init {
        pager.adapter = adapter
        pager.addOnPageChangeListener(pagerListener)
        pager.tapListener = { event ->
            host.onTap(event.x / pager.width.coerceAtLeast(1), event.y / pager.height.coerceAtLeast(1))
        }
        pager.longTapListener = {
            val page = adapter.items.getOrNull(pager.currentItem) as? ReaderPage
            if (page != null) {
                host.onLongTap(page)
                true
            } else {
                false
            }
        }
    }

    override fun setChapters(chapters: ViewerChapters) {
        tracker.setChapters(chapters)
    }

    /**
     * Mihon's `setChaptersInternal`: the listener is removed while the items
     * change so the views don't report positions from an old list, and the new
     * list is followed by the item that is still on screen. The first chapter
     * moves to the chapter's requested page; after that the pager stays on
     * the item the user is looking at, including across a chapter boundary.
     */
    private fun setChaptersNow(chapters: ViewerChapters) {
        pager.removeOnPageChangeListener(pagerListener)
        val position = items.setChapters(
            chapters = chapters,
            forceTransition = adapter.items.getOrNull(pager.currentItem) is ChapterTransition,
            currentPosition = pager.currentItem,
        )
        adapter.notifyDataSetChanged()
        val pages = chapters.currChapter.pages
        when {
            position >= 0 && position != pager.currentItem -> pager.setCurrentItem(position, false)
            firstLayout && pages != null && pages.isNotEmpty() -> {
                val target = pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)]
                moveToPage(target)
                firstLayout = false
            }
        }
        pager.addOnPageChangeListener(pagerListener)
        // Set the active page manually because settingCurrentItem doesn't trigger the listener.
        tracker.onPageChange(pager.currentItem)
    }

    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position >= 0) {
            pager.setCurrentItem(position, false)
            tracker.onPageChange(position)
        }
    }

    override fun moveForward() = move(if (readingRtl) -1 else 1)

    override fun moveBackward() = move(if (readingRtl) 1 else -1)

    private fun move(delta: Int) {
        // Mihon gives a zoomed page the tap before turning it: panning to the
        // edge and then tapping again turns the page.
        val page = tracker.currentPage as? ReaderPage
        val holder = pager.children
            .filterIsInstance<PagerPageHolder>()
            .firstOrNull { it.tag === page }
        if (holder != null) {
            if (delta > 0 && holder.canPanRight()) {
                holder.panRight()
                return
            }
            if (delta < 0 && holder.canPanLeft()) {
                holder.panLeft()
                return
            }
        }
        val next = (pager.currentItem + delta).coerceIn(0, adapter.count - 1)
        if (next != pager.currentItem) {
            pager.setCurrentItem(next, host.settings.pageTransitions)
        }
    }

    override fun destroy() {
        pager.adapter = null
    }

    private inner class ReaderPagerAdapter : PagerAdapter() {
        val items: List<Any> get() = this@PagerViewer.items.items

        override fun getCount(): Int = items.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val item = items[position]
            val view: View = when (item) {
                is ReaderPage -> PagerPageHolder(context, host, item)
                is ChapterTransition -> PagerTransitionHolder(context, item, host)
                else -> error("Unknown reader item")
            }
            view.tag = item
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            (obj as? View)?.let {
                if (it is PagerPageHolder) it.recycle()
                container.removeView(it)
            }
        }

        override fun getItemPosition(obj: Any): Int {
            val item = (obj as? View)?.tag
            return if (items.any { it === item || it == item }) {
                androidx.viewpager.widget.PagerAdapter.POSITION_UNCHANGED
            } else {
                androidx.viewpager.widget.PagerAdapter.POSITION_NONE
            }
        }

        override fun getPageTitle(position: Int): CharSequence? = null
    }
}

/** Shared page progress, error panel and watchdog for both viewers. */
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

/** Pager page holder, ported from `PagerPageHolder.kt`. */
private class PagerPageHolder(
    context: Context,
    host: ReaderViewerHost,
    page: ReaderPage,
) : ReaderPageImageView(context) {

    private val state = ReaderPageState(this, host) { ready ->
        setPage(ready, host.settings, host.sourceId)
    }

    init {
        onImageLoaded = { state.onImageLoaded() }
        onImageLoadError = { e -> state.onImageError(e) }
        state.bind(page)
    }

    override fun onDetachedFromWindow() {
        state.recycle()
        super.onDetachedFromWindow()
    }

    override fun recycle() {
        state.recycle()
        super.recycle()
    }
}

/** Pager chapter transition holder, ported from `PagerTransitionHolder.kt`. */
private class PagerTransitionHolder(
    context: Context,
    private val transition: ChapterTransition,
    private val host: ReaderViewerHost,
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

/** Webtoon viewer, ported from `WebtoonViewer.kt` and `WebtoonAdapter.kt`. */
class WebtoonViewer(
    context: Context,
    private val host: ReaderViewerHost,
) : ReaderViewer {

    private val recycler = WebtoonRecyclerView(context)
    private val frame = WebtoonFrame(context)
    private val scrollDistance = context.resources.displayMetrics.heightPixels * 3 / 4
    private val layoutManager = androidx.recyclerview.widget.WebtoonLayoutManager(context, scrollDistance)
    private val webtoonItems = WebtoonItems()
    private val adapter = WebtoonAdapter()
    private val tracker = WebtoonPageTracker(host, webtoonItems, HIDE_MENU_DISTANCE)
    /** Until the first layout, the recycler must scroll to the requested page. */
    private var firstLayout = true

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
        recycler.doubleTapZoom = host.settings.doubleTapZoom
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
            val child = recycler.findChildViewUnder(event.x, event.y)
            val page = child?.let { webtoonItems.items.getOrNull(recycler.getChildAdapterPosition(it)) } as? ReaderPage
            if (page != null) {
                host.onLongTap(page)
                true
            } else {
                false
            }
        }
        frame.addView(recycler)
    }

    override fun setChapters(chapters: ViewerChapters) {
        val old = webtoonItems.items
        // Keep the transition while the user is on it, or it would disappear
        // from under their finger the moment the chapter finishes loading.
        val forceTransition = tracker.currentPage is ChapterTransition
        webtoonItems.setChapters(chapters, forceTransition)
        val items = webtoonItems.items
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos] === items[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = true
        }).dispatchUpdatesTo(adapter)

        // The chapter menu navigates through pages, so the first layout goes
        // to the chapter's requested page; after that the list keeps the
        // position of the page the user is on.
        if (firstLayout) {
            val pages = chapters.currChapter.pages
            if (pages != null && pages.isNotEmpty()) {
                moveToPage(pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)])
                firstLayout = false
            }
        }
    }

    override fun moveToPage(page: ReaderPage) {
        val position = webtoonItems.items.indexOf(page)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            if (layoutManager.findLastEndVisibleItemPosition() == -1) {
                tracker.onScrolled(position)
            }
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

    private inner class WebtoonAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = webtoonItems.items.size

        override fun getItemViewType(position: Int): Int =
            if (webtoonItems.items[position] is ReaderPage) PAGE_VIEW else TRANSITION_VIEW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == PAGE_VIEW) {
                WebtoonPageHolder(ReaderPageImageView(parent.context, isWebtoon = true), host)
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

/** Webtoon page holder, ported from `WebtoonPageHolder.kt`. */
private class WebtoonPageHolder(
    private val frameView: ReaderPageImageView,
    private val host: ReaderViewerHost,
) : RecyclerView.ViewHolder(frameView) {

    private val state = ReaderPageState(frameView, host) { ready ->
        frameView.setPage(ready, host.settings, host.sourceId)
    }

    init {
        val context = frameView.context
        frameView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            placeholderHeight(),
        )
        val padding = (host.settings.webtoonPadding / 100f * context.resources.displayMetrics.widthPixels).toInt()
        val gap = if (host.settings.mode == com.kodastream.app.manga.MangaReadingMode.WEBTOON_GAPS) {
            (context.resources.displayMetrics.density * 8).toInt()
        } else {
            0
        }
        frameView.setPadding(padding, gap, padding, gap)
        frameView.onImageLoaded = { onPageDecoded() }
        frameView.onImageLoadError = { e -> state.onImageError(e) }
    }

    fun bind(page: ReaderPage) {
        // ReaderPageImageView.recycle clears the bound image, so the holder
        // restores its listeners every time the view comes back from the pool.
        frameView.onImageLoaded = { onPageDecoded() }
        frameView.onImageLoadError = { e -> state.onImageError(e) }
        setFrameHeight(placeholderHeight())
        state.bind(page)
    }

    /**
     * The placeholder keeps the list from jumping while the header decodes;
     * the real aspect ratio takes over as soon as the page can be drawn.
     */
    private fun onPageDecoded() {
        state.onImageLoaded()
        setFrameHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun placeholderHeight(): Int =
        (frameView.resources.displayMetrics.widthPixels * 1.45f).toInt()

    /**
     * Never replace an attached item's LayoutParams: a fresh instance has no
     * ViewHolder, and RecyclerView crashes in layout with a null holder.
     */
    private fun setFrameHeight(height: Int) {
        val params = frameView.layoutParams
        if (params is RecyclerView.LayoutParams) {
            if (params.width != ViewGroup.LayoutParams.MATCH_PARENT || params.height != height) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = height
                frameView.requestLayout()
            }
        } else {
            frameView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
            )
        }
    }

    fun recycle() {
        state.recycle()
        frameView.recycle()
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

private fun dp(value: Int): Int =
    (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
