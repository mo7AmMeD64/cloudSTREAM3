// Ported from Mihon's data/download/Downloader.kt (Apache-2.0), adapted to
// KodaStream's MangaDownload queue and MangaPrefs.
package com.kodastream.app.manga.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaChapterStore
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.ui.manga.toSChapter
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/** One queue row's live status and page progress. */
data class MangaDownloadInfo(
    val state: MangaDownload.State = MangaDownload.State.NOT_DOWNLOADED,
    val progress: Int = 0,
)

/**
 * The download queue and its workers. Chapters of different sources run side by
 * side, chapters of one source run in order, and the pages of a chapter use the
 * parallel page limit, like Mihon.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MangaDownloader(
    private val context: Context,
    private val provider: MangaDownloadProvider,
    private val notifier: MangaDownloadNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queue = MutableStateFlow<List<MangaDownload>>(emptyList())
    val queue: StateFlow<List<MangaDownload>> = _queue.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Status and progress for every queued chapter, keyed by the download key. */
    val queueInfo: StateFlow<Map<String, MangaDownloadInfo>> = _queue
        .flatMapLatest { queue ->
            if (queue.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    queue.map { download ->
                        combine(download.statusFlow, download.progressFlow) { state, progress ->
                            download.key to MangaDownloadInfo(state, progress)
                        }
                    },
                ) { it.toMap() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val lock = Mutex()

    @Volatile
    private var job: Job? = null

    @Volatile
    var isPaused: Boolean = false
        private set

    suspend fun addAll(downloads: List<MangaDownload>) = lock.withLock {
        if (downloads.isEmpty()) return@withLock
        _queue.value = _queue.value.toMutableList().apply {
            val known = mapTo(HashSet()) { it.key }
            downloads.filter { it.key !in known }.forEach {
                it.status = MangaDownload.State.QUEUE
                add(it)
            }
        }
        MangaDownloadStore.addAll(context, _queue.value.map { it.toRecord() })
    }

    /** Starts the queue. Returns false when there is nothing to run. */
    suspend fun start(): Boolean = lock.withLock { startLocked() }

    private fun startLocked(): Boolean {
        if (job?.isActive == true || _queue.value.isEmpty()) return false
        isPaused = false
        _queue.value.forEach { download ->
            if (download.status != MangaDownload.State.DOWNLOADED) {
                download.status = MangaDownload.State.QUEUE
            }
        }
        _isRunning.value = true
        job = scope.launch { runQueue() }
        return true
    }

    /** Pauses, keeping the queue for a later start. */
    suspend fun pause() {
        val running = lock.withLock {
            if (job?.isActive != true) return
            isPaused = true
            val running = job
            job = null
            _queue.value.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING) {
                    download.status = MangaDownload.State.QUEUE
                }
            }
            _isRunning.value = false
            running
        }
        // Cancelling outside the lock: the job also takes it to finish a chapter.
        running?.cancel()
        running?.join()
        notifier.onPaused()
    }

    /** Empties the queue without deleting downloaded chapters. */
    suspend fun clearQueue() {
        val running = lock.withLock {
            val running = job
            job = null
            _queue.value.forEach { download ->
                if (download.status == MangaDownload.State.DOWNLOADING ||
                    download.status == MangaDownload.State.QUEUE
                ) {
                    download.status = MangaDownload.State.NOT_DOWNLOADED
                }
            }
            _queue.value = emptyList()
            MangaDownloadStore.clear(context)
            _isRunning.value = false
            running
        }
        running?.cancel()
        running?.join()
        notifier.onComplete()
    }

    /** Removes [chapters] from the queue; a running one stops after its page. */
    suspend fun remove(chapters: List<MangaChapter>) = lock.withLock {
        val urls = chapters.mapTo(HashSet()) { it.url }
        val removed = _queue.value.filter { it.chapter.url in urls }
        removed.forEach { download ->
            if (download.status == MangaDownload.State.DOWNLOADING ||
                download.status == MangaDownload.State.QUEUE
            ) {
                download.status = MangaDownload.State.NOT_DOWNLOADED
            }
        }
        _queue.value = _queue.value - removed.toSet()
        MangaDownloadStore.remove(context, removed.map { it.toRecord() })
    }

    /** Puts already-failed chapters back in the queue. */
    suspend fun retry(chapters: List<MangaChapter>) = lock.withLock {
        val urls = chapters.mapTo(HashSet()) { it.url }
        _queue.value.filter { it.chapter.url in urls }.forEach {
            it.status = MangaDownload.State.QUEUE
        }
    }

    private suspend fun runQueue() {        try {
            while (true) {
                val pending = _queue.value.filter {
                    it.status == MangaDownload.State.QUEUE ||
                        it.status == MangaDownload.State.DOWNLOADING
                }
                if (pending.isEmpty()) break

                val perSource = max(1, MangaPrefs.downloadParallelSources(context))
                val pageSlots = max(1, MangaPrefs.downloadParallelPages(context))
                val groups = pending.groupBy { it.source }.values.take(perSource)

                coroutineScope {
                    groups.map { downloads ->
                        async(Dispatchers.IO) {
                            val slots = Semaphore(pageSlots)
                            downloads.forEach { download ->
                                // A chapter cancelled while its source waited keeps
                                // its NOT_DOWNLOADED state; don't fetch it anyway.
                                if (download.status == MangaDownload.State.QUEUE) {
                                    downloadChapter(download, slots)
                                }
                                if (download.status == MangaDownload.State.DOWNLOADED) {
                                    removeFromQueue(download)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            _isRunning.value = false
            if (isPaused) {
                notifier.onPaused()
            } else {
                notifier.onComplete()
            }
        }
    }

    private suspend fun removeFromQueue(download: MangaDownload) {
        lock.withLock {
            _queue.value = _queue.value - download
            MangaDownloadStore.remove(context, listOf(download.toRecord()))
        }
        if (download.status != MangaDownload.State.ERROR) {
            download.status = MangaDownload.State.NOT_DOWNLOADED
        }
    }

    private suspend fun downloadChapter(download: MangaDownload, slots: Semaphore) {
        val mangaDir = provider.getMangaDir(download.manga).getOrElse { error ->
            download.status = MangaDownload.State.ERROR
            notifier.onError(error.message)
            return
        }

        val available = MangaDiskUtil.getAvailableStorageSpace(mangaDir)
        if (available != -1L && available < MIN_DISK_SPACE) {
            download.status = MangaDownload.State.ERROR
            notifier.onError(context.getString(com.kodastream.app.R.string.manga_download_no_space))
            return
        }

        val chapterDirName = provider.getChapterDirName(download.chapter)
        // A previous run can have moved the chapter into place without
        // removing it from the queue; never save a second copy next to it.
        if (mangaDir.findFile(chapterDirName) != null ||
            mangaDir.findFile("$chapterDirName.cbz") != null
        ) {
            mangaDir.findFile("$chapterDirName$TMP_SUFFIX")?.delete()
            MangaDownloadCache.add(download.manga, download.chapter.url)
            download.status = MangaDownload.State.DOWNLOADED
            return
        }

        val tmpDir = mangaDir.createDirectory("$chapterDirName$TMP_SUFFIX") ?: run {
            download.status = MangaDownload.State.ERROR
            notifier.onError(context.getString(com.kodastream.app.R.string.manga_download_storage_error))
            return
        }

        try {
            val pages = download.pages ?: run {
                val fetched = download.source.getPageList(download.chapter.toSChapter())
                if (fetched.isEmpty()) {
                    throw IllegalStateException(
                        context.getString(com.kodastream.app.R.string.manga_reader_no_pages),
                    )
                }
                fetched.mapIndexed { index, page -> Page(index, page.url, page.imageUrl, page.uri) }
                    .also { download.pages = it }
            }

            // The user cancelled while the page list was being fetched.
            if (download.status != MangaDownload.State.QUEUE) {
                tmpDir.delete()
                return
            }

            download.status = MangaDownload.State.DOWNLOADING

            coroutineScope {
                pages.forEach { page ->
                    slots.acquire()
                    launch(Dispatchers.IO) {
                        downloadPage(page, download, tmpDir)
                        notifier.onProgress(download)
                    }.invokeOnCompletion { slots.release() }
                }
            }

            // The user removed it from the queue while the pages were running.
            if (download.status != MangaDownload.State.DOWNLOADING) {
                tmpDir.delete()
                return
            }

            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = MangaDownload.State.ERROR
                return
            }

            if (MangaPrefs.saveChaptersAsCBZ(context)) {
                archiveChapter(mangaDir, chapterDirName, tmpDir)
            } else {
                tmpDir.renameTo(chapterDirName)
            }
            MangaDownloadCache.add(download.manga, download.chapter.url)
            download.status = MangaDownload.State.DOWNLOADED
            markChapterDownloaded(download)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            download.status = MangaDownload.State.ERROR
            notifier.onError(e.message)
        }
    }

    /**
     * On a first download the chapter may not be in the chapter store yet (for
     * example when it was opened straight from a source search).
     */
    private suspend fun markChapterDownloaded(download: MangaDownload) {
        withContext(Dispatchers.IO) {
            val cached = MangaChapterStore.chapters(context, download.manga.sourceId, download.manga.url)
            if (cached.none { it.url == download.chapter.url }) {
                MangaChapterStore.saveChapters(
                    context,
                    download.manga.sourceId,
                    download.manga.url,
                    cached + download.chapter,
                )
            }
        }
    }

    private suspend fun downloadPage(page: Page, download: MangaDownload, tmpDir: UniFile) {
        if (download.status != MangaDownload.State.DOWNLOADING) return
        if (page.imageUrl.isNullOrBlank()) {
            page.status = Page.State.LoadPage
            page.imageUrl = runCatching { download.source.getImageUrl(page) }.getOrNull()
        }
        val imageUrl = page.imageUrl ?: return

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)

        val alreadyDownloaded = tmpDir.listFiles().orEmpty().firstOrNull {
            val name = it.name ?: return@firstOrNull false
            !name.endsWith(".tmp") &&
                (name.startsWith("$filename.") || name.startsWith("${filename}__001."))
        }
        if (alreadyDownloaded != null) {
            page.progress = 100
            page.status = Page.State.Ready
            return
        }

        try {
            downloadImage(page, download, imageUrl, tmpDir, filename)
            page.progress = 100
            page.status = Page.State.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            page.progress = 0
            page.status = Page.State.Error(e)
        }
    }

    private suspend fun downloadImage(
        page: Page,
        download: MangaDownload,
        imageUrl: String,
        tmpDir: UniFile,
        filename: String,
    ) {
        page.status = Page.State.DownloadImage
        page.progress = 0

        var attempt = 0
        while (true) {
            try {
                val tmp = tmpDir.findFile("$filename.tmp")
                    ?: tmpDir.createFile("$filename.tmp")
                    ?: throw IllegalStateException("Could not create the page file")
                val existing = tmp.length()
                download.source.getImage(page, existing).use { response ->
                    val body = response.body
                    tmp.openOutputStream(response.code == 206).use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                    val extension = body.contentType()?.let { type ->
                        if (type.type == "image") imageExtension(type.subtype) else null
                    } ?: tmp.openInputStream().use { MangaDiskUtil.findImageExtension(it) }
                        ?: "jpg"
                    tmp.renameTo("$filename.$extension")
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code == 416) tmpDir.findFile("$filename.tmp")?.delete()
                throw e
            } catch (e: Throwable) {
                if (attempt >= MAX_RETRIES) throw e
                delay(RETRY_BASE_MS shl attempt)
                attempt++
            }
        }
    }

    /** Mirrors ImageUtil.getExtensionFromMimeType: only formats the reader can decode. */
    private fun imageExtension(subtype: String): String? = when (subtype.lowercase()) {
        "jpeg" -> "jpg"
        "png", "gif", "webp", "avif", "bmp" -> subtype.lowercase()
        else -> null
    }

    private fun isDownloadSuccessful(download: MangaDownload, tmpDir: UniFile): Boolean {
        val pageCount = download.pages?.size ?: return false
        if (download.downloadedImages != pageCount) return false
        val files = tmpDir.listFiles().orEmpty().count { file ->
            val name = file.name.orEmpty()
            name != MangaDiskUtil.NOMEDIA_FILE && !name.endsWith(".tmp")
        }
        return files == pageCount
    }

    private fun archiveChapter(mangaDir: UniFile, chapterDirName: String, tmpDir: UniFile) {
        val archive = mangaDir.createFile("$chapterDirName.cbz$TMP_SUFFIX")
            ?: throw IllegalStateException("Could not create the chapter archive")
        ZipOutputStream(BufferedOutputStream(archive.openOutputStream())).use { zip ->
            tmpDir.listFiles().orEmpty()
                .filter { it.name != null && it.name != MangaDiskUtil.NOMEDIA_FILE }
                .sortedBy { it.name }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name!!))
                    file.openInputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        archive.renameTo("$chapterDirName.cbz")
        tmpDir.delete()
    }

    private companion object {
        const val TMP_SUFFIX = "_tmp"
        const val MAX_RETRIES = 3
        const val RETRY_BASE_MS = 2_000L

        /** Mihon's arbitrary minimum free space before starting a chapter. */
        const val MIN_DISK_SPACE = 200L * 1024 * 1024
    }
}
