package com.kodastream.app.manga.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.manga.ChapterProgress
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaChapterStore
import com.kodastream.app.manga.MangaHistoryEntry
import com.kodastream.app.manga.MangaHistoryStore
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.MangaReadingMode
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.clampPage
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.isLastPage
import com.kodastream.app.manga.readingOrder
import com.kodastream.app.manga.store.MangaStoreRepository
import com.kodastream.app.ui.manga.toModel
import com.kodastream.app.ui.manga.toSChapter
import com.kodastream.app.ui.manga.toSManga
import com.kodastream.app.manga.MangaEntry
import eu.kanade.tachiyomi.compat.MangaEngine
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A viewer update with a version, because equal ViewerChapters do not emit. */
data class ReaderViewerState(
    val chapters: ViewerChapters? = null,
    val version: Long = 0,
)

/**
 * Ported from Mihon's `ReaderViewModel` chapter-switching and progress logic,
 * backed by the `manga_*` stores instead of SQLDelight.
 */
class MangaReaderViewModel(
    application: Application,
    val ref: MangaRef,
    private val initialChapterUrl: String,
    private val initialPage: Int,
) : AndroidViewModel(application) {

    private val app = application
    private var source: HttpSource? = null
    private var chapterLoader: ChapterLoader? = null
    private val chaptersByUrl = HashMap<String, ReaderChapter>()
    private var ordered: List<MangaChapter> = emptyList()
    private var overlayChapters: List<ReaderOverlayChapter> = emptyList()

    private val _overlay = MutableStateFlow(
        ReaderOverlayState(
            mangaTitle = ref.title,
            settings = MangaPrefs.readerSettings(app, ref.sourceId, ref.url),
            loading = true,
        ),
    )
    val overlay: StateFlow<ReaderOverlayState> = _overlay.asStateFlow()

    private val _viewer = MutableStateFlow(ReaderViewerState())
    val viewer: StateFlow<ReaderViewerState> = _viewer.asStateFlow()

    private var viewerChapters: ViewerChapters? = null
    private var currentChapter: ReaderChapter? = null
    private var currentIndex = 0
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                // The reader can be relaunched into a fresh process (notification,
                // recents, process death) before the manga section has loaded the
                // extensions. Without this the source looks uninstalled.
                val manager = MangaSourceManager.get(app)
                if (manager.sources.value.isEmpty()) {
                    val stores = MangaStoreRepository.get(app)
                    stores.ensureDefaultStore()
                    manager.ensureLoaded { stores.stores.value }
                }
                MangaEngine.get(app)
                source = manager.catalogueSourceById(ref.sourceId) as? HttpSource
                chapterLoader = source?.let { ChapterLoader(app, it) }
                if (source == null) {
                    _overlay.value = _overlay.value.copy(
                        loading = false,
                        error = "This source is not installed.",
                    )
                    return@launch
                }
                init()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _overlay.value = _overlay.value.copy(
                    loading = false,
                    error = e.message ?: "Could not open this chapter.",
                )
            }
        }
    }

    private suspend fun init() {
        try {
            // The cached chapter list can be large; never parse it on the UI thread.
            var chapters = withContext(Dispatchers.IO) {
                MangaChapterStore.chapters(app, ref.sourceId, ref.url)
            }
            if (chapters.isEmpty()) {
                chapters = fetchChapters()
            }
            if (chapters.isEmpty()) {
                _overlay.value = _overlay.value.copy(
                    loading = false,
                    error = "The source returned no chapters for this title.",
                )
                return
            }
            ordered = readingOrder(chapters)
            buildOverlayChapters()
            val start = ordered.indexOfFirst { it.url == initialChapterUrl }
                .takeIf { it >= 0 } ?: 0
            loadChapterAt(start, initialPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _overlay.value = _overlay.value.copy(loading = false, error = e.message ?: "Could not load this chapter.")
        }
    }

    private suspend fun fetchChapters(): List<MangaChapter> {
        val http = source ?: return emptyList()
        val manga = MangaEntry(
            sourceId = ref.sourceId,
            url = ref.url,
            title = ref.title,
            thumbnailUrl = ref.thumbnailUrl,
        ).toSManga()
        val update = withContext(Dispatchers.IO) {
            http.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
        }
        val chapters = update.chapters.mapIndexedNotNull { index, chapter -> chapter.toModel(index) }
        if (chapters.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                MangaChapterStore.saveChapters(app, ref.sourceId, ref.url, chapters)
            }
        }
        return chapters
    }

    private fun chapterFor(chapter: MangaChapter): ReaderChapter =
        chaptersByUrl.getOrPut(chapter.url) { ReaderChapter(chapter) }

    private suspend fun loadChapterAt(index: Int, page: Int = 0, resumeProgress: Boolean = true) {
        val loader = chapterLoader ?: return
        val model = ordered.getOrNull(index) ?: return
        val chapter = chapterFor(model)
        val progress = if (resumeProgress) {
            withContext(Dispatchers.IO) {
                MangaChapterStore.progressFor(app, ref.sourceId, ref.url, model.url)
            }
        } else {
            null
        }
        val startPage = when {
            progress?.read == true -> 0
            progress != null && progress.lastPageRead > 0 -> progress.lastPageRead
            else -> page
        }
        currentChapter?.let { saveProgress(it, immediate = true) }
        _overlay.value = _overlay.value.copy(loading = true, error = null)

        try {
            loader.loadChapter(chapter, startPage)
            currentIndex = index
            setViewerChapters(chapter)
            _overlay.value = _overlay.value.copy(
                loading = false,
                error = null,
                noPages = false,
                noPagesDetail = null,
                chapterName = chapter.chapter.name,
                chapterUrl = chapter.chapter.url,
                chapterNumber = chapter.chapter.number,
                chapterIndex = index,
                chapterCount = ordered.size,
                chapters = overlayChapters,
                pageCount = chapter.pages?.size ?: 0,
                page = startPage,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _overlay.value = _overlay.value.copy(
                loading = false,
                error = e.message ?: "Could not load this chapter.",
                noPages = e is EmptyChapterException,
                noPagesDetail = (e as? EmptyChapterException)?.detail,
                chapterName = chapter.chapter.name,
                chapterUrl = chapter.chapter.url,
                chapterIndex = index,
                chapterCount = ordered.size,
                chapters = overlayChapters,
            )
        }
    }

    private fun setViewerChapters(chapter: ReaderChapter) {
        val prev = ordered.getOrNull(currentIndex - 1)?.let(::chapterFor)
        val next = ordered.getOrNull(currentIndex + 1)?.let(::chapterFor)
        val nextViewer = ViewerChapters(chapter, prev, next)
        nextViewer.ref()
        viewerChapters?.unref()
        viewerChapters = nextViewer
        currentChapter = chapter
        _viewer.value = ReaderViewerState(nextViewer, _viewer.value.version + 1)
    }

    /** The chapter drawer's rows, rebuilt only when the chapter list changes. */
    private fun buildOverlayChapters() {
        overlayChapters = ordered.mapIndexed { index, chapter ->
            ReaderOverlayChapter(
                index = index,
                name = chapter.name,
                number = chapter.number,
                read = false,
            )
        }
    }

    fun requestPreloadChapter(chapter: ReaderChapter) {
        viewModelScope.launch(Dispatchers.IO) {
            preloadChapter(chapter)
            withContext(Dispatchers.Main) {
                val viewer = viewerChapters ?: return@withContext
                if (viewer.prevChapter === chapter ||
                    viewer.nextChapter === chapter ||
                    viewer.currChapter === chapter
                ) {
                    _viewer.value = ReaderViewerState(viewer, _viewer.value.version + 1)
                }
            }
        }
    }

    /** Rebuilds a chapter whose displayed pages lost their loader. */
    fun reloadChapter(chapter: ReaderChapter) {
        if (chapter.state == ReaderChapter.State.Loading) return
        if (chapter === currentChapter) chapter.requestedPage = _overlay.value.page
        chapter.pageLoader?.recycle()
        chapter.pageLoader = null
        chapter.state = ReaderChapter.State.Wait
        requestPreloadChapter(chapter)
    }

    private suspend fun preloadChapter(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded || chapter.state == ReaderChapter.State.Loading) return
        val loader = chapterLoader ?: return
        try {
            loader.loadChapter(chapter, chapter.requestedPage)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // The transition page shows the error and a Retry button.
        }
    }

    fun onPageSelected(page: ReaderPage) {
        val chapter = page.chapter
        val pages = chapter.pages ?: return
        val pageIndex = clampPage(page.index, pages.size)
        val read = isLastPage(pageIndex, pages.size)

        if (chapter !== currentChapter) {
            currentChapter?.let { saveProgress(it, immediate = true) }
            currentIndex = ordered.indexOfFirst { it.url == chapter.chapter.url }.takeIf { it >= 0 } ?: currentIndex
            setViewerChapters(chapter)
        }

        _overlay.value = _overlay.value.copy(
            chapterName = chapter.chapter.name,
            chapterUrl = chapter.chapter.url,
            chapterNumber = chapter.chapter.number,
            bookmarked = MangaChapterStore.progressFor(
                app, ref.sourceId, ref.url, chapter.chapter.url,
            )?.bookmarked == true,
            chapterIndex = currentIndex,
            chapterCount = ordered.size,
            page = pageIndex,
            pageCount = pages.size,
            loading = false,
            error = null,
        )

        scheduleProgress(chapter, pageIndex, pages.size, read)
    }

    override fun onCleared() {
        saveProgress(currentChapter, immediate = true)
        viewerChapters?.unref()
        viewerChapters = null
        super.onCleared()
    }

    fun saveProgressNow() {
        saveProgress(currentChapter, immediate = true)
    }

    private fun scheduleProgress(chapter: ReaderChapter, page: Int, pageCount: Int, read: Boolean) {
        saveJob?.cancel()
        if (read) {
            saveProgress(chapter, page, pageCount, read = true)
            return
        }
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveProgress(chapter, page, pageCount, read = false)
        }
    }

    private fun saveProgress(chapter: ReaderChapter?, immediate: Boolean = false) {
        chapter ?: return
        saveJob?.cancel()
        saveJob = null
        val pages = chapter.pages ?: return
        val overlay = _overlay.value
        val page = if (chapter === currentChapter) overlay.page else chapter.requestedPage
        val pageIndex = clampPage(page, pages.size)
        val read = isLastPage(pageIndex, pages.size)
        saveProgress(chapter, pageIndex, pages.size, read)
    }

    private fun saveProgress(chapter: ReaderChapter, page: Int, pageCount: Int, read: Boolean) {
        val old = MangaChapterStore.progressFor(app, ref.sourceId, ref.url, chapter.chapter.url)
            ?: ChapterProgress()
        val now = System.currentTimeMillis()
        val nowRead = read || old.read
        MangaChapterStore.saveProgress(
            app,
            ref.sourceId,
            ref.url,
            chapter.chapter.url,
            ChapterProgress(
                read = nowRead,
                lastPageRead = page,
                pageCount = pageCount,
                bookmarked = old.bookmarked,
                readAt = if (read && !old.read) now else old.readAt,
            ),
        )
        if (read && !old.read) {
            MangaLibraryStore.setLastRead(app, ref.sourceId, ref.url, now)
        }
        MangaHistoryStore.record(
            app,
            MangaHistoryEntry(
                sourceId = ref.sourceId,
                mangaUrl = ref.url,
                title = ref.title,
                thumbnailUrl = ref.thumbnailUrl,
                chapterUrl = chapter.chapter.url,
                chapterName = chapter.chapter.name,
                chapterNumber = chapter.chapter.number,
                page = page,
                pageCount = pageCount,
                readAt = now,
            ),
        )
    }

    /** Menu bookmark toggle; returns the new state. */
    fun toggleBookmark(chapter: MangaChapter): Boolean {
        val old = MangaChapterStore.progressFor(app, ref.sourceId, ref.url, chapter.url)
        val next = old?.bookmarked != true
        MangaChapterStore.setBookmarked(app, ref.sourceId, ref.url, chapter.url, next)
        _overlay.value = _overlay.value.copy(bookmarked = next)
        return next
    }

    fun currentChapter(): MangaChapter? = currentChapter?.chapter

    fun chapterWebUrl(chapter: MangaChapter? = currentChapter?.chapter): String? {
        val http = source ?: return null
        val target = chapter ?: return null
        return runCatching { http.getChapterUrl(target.toSChapter()) }.getOrNull()
    }

    fun mangaWebUrl(): String? {
        val http = source ?: return null
        val manga = MangaEntry(
            sourceId = ref.sourceId,
            url = ref.url,
            title = ref.title,
            thumbnailUrl = ref.thumbnailUrl,
        ).toSManga()
        return runCatching { http.getMangaUrl(manga) }.getOrNull()
    }

    /** Menu chapter arrows and the details screen's read marks. */
    suspend fun openAdjacent(delta: Int): ReaderChapter? {
        val index = (currentIndex + delta).coerceIn(0, ordered.lastIndex)
        if (index == currentIndex) return null
        loadChapterAt(index, resumeProgress = false)
        return currentChapter
    }

    /** A row in the chapter drawer was picked. */
    fun openChapter(index: Int) {
        if (index == currentIndex) {
            setMenuVisible(false)
            return
        }
        viewModelScope.launch {
            loadChapterAt(index.coerceIn(0, ordered.lastIndex), resumeProgress = false)
            setMenuVisible(false)
        }
    }

    fun retry() {
        val index = currentIndex
        currentChapter?.let {
            it.state = ReaderChapter.State.Wait
            it.pageLoader = null
        }
        viewModelScope.launch { loadChapterAt(index) }
    }

    fun setMenuVisible(visible: Boolean) {
        _overlay.value = _overlay.value.copy(menuVisible = visible)
    }

    fun toggleMenu() {
        _overlay.value = _overlay.value.copy(menuVisible = !_overlay.value.menuVisible)
    }

    fun setReadingMode(mode: MangaReadingMode) {
        MangaPrefs.setReaderModeFor(app, ref.sourceId, ref.url, mode)
        val settings = MangaPrefs.readerSettings(app, ref.sourceId, ref.url)
        _overlay.value = _overlay.value.copy(settings = settings)
    }

    fun updateSettings(settings: MangaReaderSettings) {
        val prefs = MangaPrefs
        prefs.setReaderTapLayout(app, settings.tapLayout)
        prefs.setReaderInvert(app, settings.tapInvert)
        prefs.setReaderScaleType(app, settings.scaleType)
        prefs.setReaderZoomStart(app, settings.zoomStart)
        prefs.setReaderCropBorders(app, settings.cropBorders)
        prefs.setReaderPageTransitions(app, settings.pageTransitions)
        prefs.setReaderTheme(app, settings.theme)
        prefs.setReaderKeepScreenOn(app, settings.keepScreenOn)
        prefs.setReaderShowPageNumber(app, settings.showPageNumber)
        prefs.setReaderVolumeKeys(app, settings.volumeKeys)
        prefs.setReaderLongTapSave(app, settings.longTapSave)
        prefs.setReaderWebtoonPadding(app, settings.webtoonPadding)
        prefs.setReaderDoubleTapZoom(app, settings.doubleTapZoom)
        prefs.setReaderOrientation(app, settings.orientation)
        _overlay.value = _overlay.value.copy(settings = settings)
    }

    /** A chapter progress snapshot for the details screen's read marks. */
    fun chapterProgress(chapter: MangaChapter): ChapterProgress? =
        MangaChapterStore.progressFor(app, ref.sourceId, ref.url, chapter.url)

    private companion object {
        const val SAVE_DELAY_MS = 3_000L
    }
}
