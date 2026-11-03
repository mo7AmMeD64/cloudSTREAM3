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
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.kodastream.app.R
import com.kodastream.app.manga.MangaReaderSettings
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    fun onNoNextChapter()
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
    private var currentItem: Any? = null

    override val view: View get() = pager

    init {
        pager.adapter = adapter
        pager.addOnPageChangeListener(
            object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    tracker(position)
                }
            },
        )
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
        val position = items.setChapters(chapters, forceTransition = false, pager.currentItem)
        adapter.notifyDataSetChanged()
        val pages = chapters.currChapter.pages
        when {
            position >= 0 && position != pager.currentItem -> pager.setCurrentItem(position, false)
            position < 0 && pages != null && pages.isNotEmpty() ->
                moveToPage(pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)])
        }
        tracker(pager.currentItem)
    }

    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position >= 0) {
            pager.setCurrentItem(position, false)
            tracker(position)
        }
    }

    override fun moveForward() = move(if (readingRtl) -1 else 1)

    override fun moveBackward() = move(if (readingRtl) 1 else -1)

    private fun move(delta: Int) {
        val next = (pager.currentItem + delta).coerceIn(0, adapter.count - 1)
        if (next != pager.currentItem) {
            pager.setCurrentItem(next, host.settings.pageTransitions)
        }
    }

    private fun tracker(position: Int) {
        val item = adapter.items.getOrNull(position) ?: return
        if (item === currentItem) return
        val previous = currentItem
        currentItem = item
        when (item) {
            is ReaderPage -> {
                host.onPageSelected(item)
                val pages = item.chapter.pages.orEmpty()
                val inPreloadRange = pages.size - item.number < 5
                if (inPreloadRange && item.chapter === items.currentChapter) {
                    items.nextTransition?.to?.let(host::requestPreloadChapter)
                }
            }
            is ChapterTransition -> {
                host.onTransitionSelected(item)
                if (item is ChapterTransition.Next && item.to == null) host.onNoNextChapter()
                else item.to?.let(host::requestPreloadChapter)
            }
        }
        if (previous is ReaderPage && item is ReaderPage && previous.chapter !== item.chapter) {
            // A viewer that crossed a chapter boundary keeps the new chapter active.
            host.requestPreloadChapter(item.chapter)
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

/** Pager page holder, ported from `PagerPageHolder.kt`. */
private class PagerPageHolder(
    context: Context,
    private val host: ReaderViewerHost,
    private val page: ReaderPage,
) : ReaderPageImageView(context) {

    private val progress = ProgressBar(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
    }
    private val error = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 15f
        setPadding(dp(24), dp(24), dp(24), dp(24))
        isVisible = false
        setOnClickListener {
            page.status = Page.State.Queue
            page.chapter.pageLoader?.retryPage(page)
        }
    }
    private var job: Job? = null

    init {
        addView(progress)
        addView(error)
        onImageLoaded = { progress.isVisible = false }
        onImageLoadError = { e -> showError(e) }
        job = host.readerScope.launch {
            supervisorScope {
                launch(Dispatchers.IO) { page.chapter.pageLoader?.loadPage(page) }
                page.statusFlow.collectLatest { state ->
                    when (state) {
                        Page.State.Queue, Page.State.LoadPage -> showLoading()
                        Page.State.DownloadImage -> {
                            showLoading()
                            progress.isIndeterminate = false
                            progress.progress = page.progress
                        }
                        Page.State.Ready -> {
                            progress.isVisible = false
                            error.isVisible = false
                            setPage(page, host.settings, host.sourceId)
                        }
                        is Page.State.Error -> showError(state.error)
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        job?.cancel()
        job = null
        super.onDetachedFromWindow()
    }

    override fun recycle() {
        job?.cancel()
        super.recycle()
    }

    private fun showLoading() {
        progress.isVisible = true
        progress.isIndeterminate = true
        error.isVisible = false
    }

    private fun showError(e: Throwable?) {
        progress.isVisible = false
        error.isVisible = true
        error.text = e?.message ?: getContext().getString(R.string.manga_reader_page_failed)
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
    private val adapter = WebtoonAdapter()
    private var items: List<Any> = emptyList()
    private var currentItem: Any? = null
    private var chapterUrl: String? = null

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
                    val position = layoutManager.findLastEndVisibleItemPosition()
                    tracker(position)
                    val last = items.getOrNull(position)
                    if (last is ChapterTransition.Next && last.to == null) host.onNoNextChapter()
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
            val page = child?.let { items.getOrNull(recycler.getChildAdapterPosition(it)) } as? ReaderPage
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
        val old = items
        items = viewerItems(chapters, forceTransition = false)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = items.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos] === items[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = true
        }).dispatchUpdatesTo(adapter)
        val chapterChanged = chapterUrl != chapters.currChapter.chapter.url
        chapterUrl = chapters.currChapter.chapter.url
        if (chapterChanged) {
            currentItem = null
            val pages = chapters.currChapter.pages
            if (pages != null && pages.isNotEmpty()) {
                moveToPage(pages[chapters.currChapter.requestedPage.coerceIn(0, pages.lastIndex)])
            }
        }
    }

    override fun moveToPage(page: ReaderPage) {
        val position = items.indexOf(page)
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0)
            tracker(position)
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

    private fun tracker(position: Int) {
        val item = items.getOrNull(position) ?: return
        if (item === currentItem) return
        currentItem = item
        when (item) {
            is ReaderPage -> {
                host.onPageSelected(item)
                val pages = item.chapter.pages.orEmpty()
                if (pages.size - item.number < 5) {
                    items.lastOrNull { it is ChapterTransition.Next }
                        ?.let { (it as ChapterTransition).to }
                        ?.let(host::requestPreloadChapter)
                }
            }
            is ChapterTransition -> {
                host.onTransitionSelected(item)
                item.to?.let(host::requestPreloadChapter)
            }
        }
    }

    override fun destroy() {
        recycler.adapter = null
        frame.removeAllViews()
    }

    private inner class WebtoonAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int =
            if (items[position] is ReaderPage) PAGE_VIEW else TRANSITION_VIEW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == PAGE_VIEW) {
                WebtoonPageHolder(ReaderPageImageView(parent.context, isWebtoon = true), host)
            } else {
                WebtoonTransitionHolder(parent.context, host)
            }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is WebtoonPageHolder -> holder.bind(items[position] as ReaderPage)
                is WebtoonTransitionHolder -> holder.bind(items[position] as ChapterTransition)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? WebtoonPageHolder)?.recycle()
        }
    }
}

private const val PAGE_VIEW = 0
private const val TRANSITION_VIEW = 1

/** Webtoon page holder, ported from `WebtoonPageHolder.kt`. */
private class WebtoonPageHolder(
    private val frameView: ReaderPageImageView,
    private val host: ReaderViewerHost,
) : RecyclerView.ViewHolder(frameView) {

    private val progress = ProgressBar(frameView.context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
    }
    private val error = TextView(frameView.context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 15f
        setPadding(dp(24), dp(24), dp(24), dp(24))
        isVisible = false
        setOnClickListener {
            page?.let {
                it.status = Page.State.Queue
                it.chapter.pageLoader?.retryPage(it)
            }
        }
    }
    private var page: ReaderPage? = null
    private var job: Job? = null

    init {
        (frameView as FrameLayout).addView(progress)
        (frameView as FrameLayout).addView(error)
        val context = frameView.context
        frameView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.widthPixels * 1.45f).toInt(),
        )
        val padding = (host.settings.webtoonPadding / 100f * context.resources.displayMetrics.widthPixels).toInt()
        val gap = if (host.settings.mode == com.kodastream.app.manga.MangaReadingMode.WEBTOON_GAPS) {
            (context.resources.displayMetrics.density * 8).toInt()
        } else {
            0
        }
        frameView.setPadding(padding, gap, padding, gap)
    }

    fun bind(page: ReaderPage) {
        this.page = page
        job?.cancel()
        progress.isVisible = true
        progress.isIndeterminate = true
        error.isVisible = false
        frameView.onImageLoaded = { progress.isVisible = false }
        frameView.onImageLoadError = { e -> showError(e) }
        val context = frameView.context
        frameView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.widthPixels * 1.45f).toInt(),
        )
        job = host.readerScope.launch {
            supervisorScope {
                launch(Dispatchers.IO) { page.chapter.pageLoader?.loadPage(page) }
                page.statusFlow.collectLatest { state ->
                    when (state) {
                        Page.State.Queue, Page.State.LoadPage -> showLoading()
                        Page.State.DownloadImage -> {
                            showLoading()
                            progress.isIndeterminate = false
                            progress.progress = page.progress
                        }
                        Page.State.Ready -> {
                            progress.isVisible = false
                            error.isVisible = false
                            frameView.layoutParams = RecyclerView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            frameView.setPage(page, host.settings, host.sourceId)
                        }
                        is Page.State.Error -> showError(state.error)
                    }
                }
            }
        }
    }

    fun recycle() {
        job?.cancel()
        job = null
        frameView.recycle()
    }

    private fun showLoading() {
        progress.isVisible = true
        progress.isIndeterminate = true
        error.isVisible = false
    }

    private fun showError(e: Throwable?) {
        progress.isVisible = false
        error.isVisible = true
        error.text = e?.message ?: frameView.context.getString(R.string.manga_reader_page_failed)
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
