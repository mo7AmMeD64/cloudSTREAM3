package com.kodastream.app.manga.reader

import android.content.Context
import com.kodastream.app.manga.CachedPage
import com.kodastream.app.manga.MangaPageCache
import com.kodastream.app.manga.MangaPageListStore
import com.kodastream.app.manga.PageFetchInfo
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.ui.manga.toSChapter
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The page-list cache and page files, behind an interface so the loader runs
 * in JVM tests without an Android Context.
 */
interface ReaderPageStore {
    suspend fun pageList(sourceId: Long, chapterUrl: String): List<CachedPage>?
    suspend fun savePageList(sourceId: Long, chapterUrl: String, pages: List<Page>)
    suspend fun file(
        source: HttpSource,
        page: ReaderPage,
        onProgress: (Int) -> Unit,
        onFetched: (PageFetchInfo) -> Unit,
    ): File
}

/** Production [ReaderPageStore]: the existing manga page list and file cache. */
class AndroidReaderPageStore(private val context: Context) : ReaderPageStore {
    override suspend fun pageList(sourceId: Long, chapterUrl: String): List<CachedPage>? =
        MangaPageListStore.get(context, sourceId, chapterUrl)

    override suspend fun savePageList(sourceId: Long, chapterUrl: String, pages: List<Page>) {
        MangaPageListStore.save(context, sourceId, chapterUrl, pages)
    }

    override suspend fun file(
        source: HttpSource,
        page: ReaderPage,
        onProgress: (Int) -> Unit,
        onFetched: (PageFetchInfo) -> Unit,
    ): File = MangaPageCache.file(
        context,
        source,
        page,
        onProgress = onProgress,
        onFetched = onFetched,
    )
}

/**
 * Ported from Mihon's `loader/PageLoader.kt`.
 */
abstract class PageLoader {
    var isRecycled = false
        private set

    abstract var isLocal: Boolean

    abstract suspend fun getPages(): List<ReaderPage>

    open suspend fun loadPage(page: ReaderPage) {}

    open fun retryPage(page: ReaderPage) {}

    /** Stops the page's in-flight job and leaves the timeout error on it. */
    open fun abortPage(page: ReaderPage, error: Throwable) {
        page.status = Page.State.Error(error)
    }

    open fun recycle() {
        isRecycled = true
    }
}

/**
 * Ported from Mihon's `loader/HttpPageLoader.kt`. The rolling chunk rule,
 * the page-first load and the `canLoadAhead` window are kept. The image cache
 * and page list are KodaStream's own, injected through [store].
 */
class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val store: ReaderPageStore,
    private val canLoadAhead: () -> Boolean = { true },
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunkProcessLock = Any()
    private var chunkProcessJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<Int, Job>()

    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        val chapterUrl = chapter.chapter.url
        try {
            val cached = store.pageList(source.id, chapterUrl)
            val pages = cached?.map { CachedPageReader(it.url, it.imageUrl) }
                ?: source.getPageList(chapter.chapter.toSChapter()).also {
                    store.savePageList(source.id, chapterUrl, it)
                }.map { CachedPageReader(it.url, it.imageUrl) }

            return pages.mapIndexed { index, page ->
                ReaderPage(index, page.url, page.imageUrl).also { it.chapter = chapter }
            }.also {
                ReaderDiagnostics.record(
                    ReaderDiagnosticEvent(
                        chapterUrl = chapterUrl,
                        state = "page list: ${it.size} pages",
                    ),
                )
                it.loadByRollingChunked(0, 1, 1)
            }
        } catch (e: Throwable) {
            recordPageError(null, e, chapterUrl)
            throw e
        }
    }

    private data class CachedPageReader(val url: String, val imageUrl: String?)

    override suspend fun loadPage(page: ReaderPage) = withContext(Dispatchers.IO) {
        if (page.status is Page.State.Error) page.status = Page.State.Queue
        // Start the page being read before planning: planning walks the whole
        // chapter and takes chunkProcessLock.
        launchLoadOnce(page)

        val pages = page.chapter.pages.orEmpty()
        if (page.index !in pages.indices) return@withContext
        pages.loadByRollingChunked(page.index, 3, 5) { chunks ->
            val continueLoading = chunks.take(2).flatten().map { it.index }.toSet()
            downloadJobs.keys.filter { it !in continueLoading }
                .forEach { downloadJobs.remove(it)?.cancel() }
        }
    }

    override fun retryPage(page: ReaderPage) {
        page.file?.delete()
        page.file = null
        page.status = Page.State.Queue
        val index = page.index
        downloadJobs.remove(index)?.cancel()
        scope.launch { internalLoadPage(page, force = true) }.track(index)
    }

    override fun abortPage(page: ReaderPage, error: Throwable) {
        downloadJobs.remove(page.index)?.cancel()
        setState(page, Page.State.Error(error), error)
    }

    override fun recycle() {
        super.recycle()
        scope.cancel()
        downloadJobs.clear()
    }

    private fun Job.track(index: Int) = also { job ->
        downloadJobs[index] = job
        job.invokeOnCompletion { downloadJobs.remove(index, job) }
    }

    /**
     * Starts [page] unless it is already loaded or a load is in flight.
     * Claiming the slot and starting the job has to be atomic, otherwise two
     * chunk chains can both launch the same page.
     */
    private fun launchLoadOnce(page: ReaderPage): Job? {
        val index = page.index
        downloadJobs[index]?.takeIf { it.isActive }?.let { return it }
        if (page.status is Page.State.Ready) return null
        if (page.status !is Page.State.Queue && page.status !is Page.State.Error) {
            // The job that set this state was cancelled before its Queue write
            // landed. Treat the stale state as queued so the pass picks it up.
            page.status = Page.State.Queue
        }

        val job = scope.launch(start = CoroutineStart.LAZY) { internalLoadPage(page) }
        val running = downloadJobs.putIfAbsent(index, job)
        if (running != null) {
            job.cancel()
            return running
        }
        job.invokeOnCompletion { downloadJobs.remove(index, job) }
        job.start()
        return job
    }

    private fun List<ReaderPage>.loadByRollingChunked(
        index: Int,
        chunkStartSize: Int,
        chunkEndSize: Int,
        onNewChunks: (List<List<ReaderPage>>) -> Unit = {},
    ) {
        val items = this
        // When loading ahead is off, only the pages around the one being read
        // are in the window.
        val window = if (canLoadAhead()) {
            items.indices
        } else {
            (index - 1)..(index + LIMITED_PAGES_AHEAD)
        }
        val chunks: List<List<ReaderPage>> = buildList<ReaderPage> {
            // Qualified with items: inside buildList the implicit receiver is
            // the empty list being built, not the chapter's pages.
            items.getOrNull(index)?.let { add(it) }
            items.getOrNull(index - 1)?.let { add(it) }
            items.getOrNull(index + 1)?.let { add(it) }
            if (index < items.lastIndex - 1) {
                items.subList(index + 2, items.size).let(::addAll)
            }
            if (index > 1) {
                items.subList(0, index - 1).reversed().let(::addAll)
            }
        }
            .filter { it.index in window }
            .rollingChunked(chunkStartSize, chunkEndSize)
            .also(onNewChunks)

        synchronized(chunkProcessLock) {
            chunkProcessJob?.cancel()
            chunkProcessJob = scope.launch {
                for (chunk in chunks) {
                    val jobs = chunk.mapNotNull { launchLoadOnce(it) }
                    jobs.joinAll()
                    // A page whose job was cancelled by a newer plan goes back
                    // to Queue asynchronously; if that plan already passed it,
                    // nothing else would pick it up. Retry it in this pass.
                    chunk.filter { it.status is Page.State.Queue }
                        .mapNotNull { launchLoadOnce(it) }
                        .joinAll()
                }
            }
        }
    }

    private fun <T> List<T>.rollingChunked(startSize: Int, endSize: Int): List<List<T>> {
        require(startSize >= 1)
        val result = ArrayList<List<T>>()
        var chunkSize = startSize
        var index = 0
        while (index < size) {
            val sizeOfChunk = chunkSize.coerceAtMost(size - index)
            result.add(List(sizeOfChunk) { this[it + index] })
            index += chunkSize
            if (chunkSize < endSize) chunkSize += 1
        }
        return result
    }

    private suspend fun internalLoadPage(page: ReaderPage, force: Boolean = false) {
        try {
            if (page.imageUrl.isNullOrEmpty()) {
                setState(page, Page.State.LoadPage)
                page.imageUrl = source.getImageUrl(page)
            }

            val existing = page.file
            val file = if (!force && existing != null && existing.isFile && existing.length() > 0L) {
                existing
            } else {
                setState(page, Page.State.DownloadImage)
                store.file(
                    source = source,
                    page = page,
                    onProgress = { page.progress = it },
                    onFetched = { info -> recordFetched(page, info) },
                )
            }
            page.file = file
            page.stream = { file.inputStream() }
            setState(page, Page.State.Ready)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                // Chunk chains are cancelled on every page turn. A cancelled
                // page goes back to Queue so a later pass can pick it up.
                if (page.status !is Page.State.Error) {
                    page.status = Page.State.Queue
                }
                throw e
            }
            setState(page, Page.State.Error(e), e)
        }
    }

    private fun setState(page: ReaderPage, state: Page.State, error: Throwable? = null) {
        page.status = state
        ReaderDiagnostics.record(
            ReaderDiagnosticEvent(
                chapterUrl = page.chapter.chapter.url,
                pageIndex = page.index,
                state = pageStateLabel(state),
                host = page.imageUrl?.toHttpUrlOrNull()?.host,
                httpStatus = (error as? HttpException)?.code,
                exceptionClass = error?.javaClass?.simpleName,
                message = error?.let(::rootMessage),
                cloudflare = if (state is Page.State.DownloadImage) "checking" else null,
            ),
        )
    }

    private fun recordFetched(page: ReaderPage, info: PageFetchInfo) {
        ReaderDiagnostics.record(
            ReaderDiagnosticEvent(
                chapterUrl = page.chapter.chapter.url,
                pageIndex = page.index,
                state = if (info.httpCode == null) "cached" else "fetched",
                host = page.imageUrl?.toHttpUrlOrNull()?.host,
                httpStatus = info.httpCode,
                cloudflare = if (info.httpCode == null) {
                    null
                } else {
                    if (info.cloudflareSolved) "solved" else "none"
                },
            ),
        )
    }

    private fun recordPageError(page: ReaderPage?, error: Throwable, chapterUrl: String) {
        ReaderDiagnostics.record(
            ReaderDiagnosticEvent(
                chapterUrl = chapterUrl,
                pageIndex = page?.index,
                state = "error",
                host = page?.imageUrl?.toHttpUrlOrNull()?.host,
                httpStatus = (error as? HttpException)?.code,
                exceptionClass = error.javaClass.simpleName,
                message = rootMessage(error),
            ),
        )
    }
}

private const val LIMITED_PAGES_AHEAD = 4

/** The source answered, but with an empty page list. */
class EmptyChapterException : Exception("The source returned no pages for this chapter")

/**
 * Ported from Mihon's `loader/ChapterLoader.kt`, online sources only.
 */
class ChapterLoader(
    private val context: Context,
    private val source: HttpSource,
    private val canLoadAhead: () -> Boolean = { true },
    private val store: ReaderPageStore = AndroidReaderPageStore(context),
) {
    suspend fun loadChapter(chapter: ReaderChapter, requestedPage: Int) {
        if (chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null) return

        chapter.state = ReaderChapter.State.Loading
        withContext(Dispatchers.IO) {
            try {
                val loader = HttpPageLoader(chapter, source, store, canLoadAhead)
                chapter.pageLoader = loader
                val pages = loader.getPages().onEach { it.chapter = chapter }
                if (pages.isEmpty()) {
                    throw EmptyChapterException()
                }
                chapter.requestedPage = requestedPage
                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }
}
