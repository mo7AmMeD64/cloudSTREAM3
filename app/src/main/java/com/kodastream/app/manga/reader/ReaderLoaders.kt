package com.kodastream.app.manga.reader

import android.content.Context
import com.kodastream.app.manga.MangaPageCache
import com.kodastream.app.manga.MangaPageListStore
import com.kodastream.app.ui.manga.toSChapter
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
import java.util.concurrent.ConcurrentHashMap

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

    open fun recycle() {
        isRecycled = true
    }
}

/**
 * Ported from Mihon's `loader/HttpPageLoader.kt`. The rolling chunk rule,
 * chunk sizes (3 then 5), page-list cache and cancellation are kept. The image
 * cache is KodaStream's [MangaPageCache] instead of Mihon's ChapterCache.
 */
class HttpPageLoader(
    private val context: Context,
    private val chapter: ReaderChapter,
    private val source: HttpSource,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunkProcessLock = Any()
    private var chunkProcessJob: Job? = null
    private val downloadJobs = ConcurrentHashMap<Int, Job>()

    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        val cached = MangaPageListStore.get(context, source.id, chapter.chapter.url)
        val pages = cached?.map { CachedPageReader(it.url, it.imageUrl) }
            ?: source.getPageList(chapter.chapter.toSChapter()).also {
                MangaPageListStore.save(context, source.id, chapter.chapter.url, it)
            }.map { CachedPageReader(it.url, it.imageUrl) }

        return pages.mapIndexed { index, page ->
            ReaderPage(index, page.url, page.imageUrl).also { it.chapter = chapter }
        }.also {
            it.loadByRollingChunked(0, 1, 1)
        }
    }

    private data class CachedPageReader(val url: String, val imageUrl: String?)

    override suspend fun loadPage(page: ReaderPage) = withContext(Dispatchers.IO) {
        if (page.status is Page.State.Error) page.status = Page.State.Queue
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

    override fun recycle() {
        super.recycle()
        scope.cancel()
        downloadJobs.clear()
    }

    private fun Job.track(index: Int) = also { job ->
        downloadJobs[index] = job
        job.invokeOnCompletion { downloadJobs.remove(index, job) }
    }

    private fun launchLoadOnce(page: ReaderPage): Job? {
        val index = page.index
        downloadJobs[index]?.takeIf { it.isActive }?.let { return it }
        if (page.status !is Page.State.Queue && page.status !is Page.State.Error) return null

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
        val chunks: List<List<ReaderPage>> = buildList<ReaderPage> {
            getOrNull(index)?.let { add(it) }
            getOrNull(index - 1)?.let { add(it) }
            getOrNull(index + 1)?.let { add(it) }
            if (index < lastIndex - 1) addAll(subList(index + 2, size))
            if (index > 1) addAll(subList(0, index - 1).asReversed())
        }
            .rollingChunked(chunkStartSize, chunkEndSize)
            .also(onNewChunks)

        synchronized(chunkProcessLock) {
            chunkProcessJob?.cancel()
            chunkProcessJob = scope.launch {
                for (chunk in chunks) {
                    chunk.mapNotNull { launchLoadOnce(it) }.joinAll()
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
                page.status = Page.State.LoadPage
                page.imageUrl = source.getImageUrl(page)
            }

            val existing = page.file
            val file = if (!force && existing != null && existing.isFile && existing.length() > 0L) {
                existing
            } else {
                page.status = Page.State.DownloadImage
                MangaPageCache.file(context, source, page, onProgress = { page.progress = it })
            }
            page.file = file
            page.stream = { file.inputStream() }
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            if (e is CancellationException) {
                page.status = Page.State.Queue
                throw e
            }
            page.status = Page.State.Error(e)
        }
    }
}

/** The source answered, but with an empty page list. */
class EmptyChapterException : Exception("The source returned no pages for this chapter")

/**
 * Ported from Mihon's `loader/ChapterLoader.kt`, online sources only.
 */
class ChapterLoader(
    private val context: Context,
    private val source: HttpSource,
) {
    suspend fun loadChapter(chapter: ReaderChapter, requestedPage: Int) {
        if (chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null) return

        chapter.state = ReaderChapter.State.Loading
        withContext(Dispatchers.IO) {
            try {
                val loader = HttpPageLoader(context, chapter, source)
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
