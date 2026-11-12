// Ported from Mihon's data/download/DownloadManager.kt (Apache-2.0), adapted to
// KodaStream's MangaEntry/MangaChapter models and the app's extension loader.
package com.kodastream.app.manga.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.key
import com.kodastream.app.manga.store.MangaStoreRepository
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single owner of the chapter download queue. The worker owns the process
 * lifecycle; this class is what the UI talks to.
 */
class MangaDownloadManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val provider = MangaDownloadProvider(app)
    private val notifier = MangaDownloadNotifier(app)
    private val downloader = MangaDownloader(app, provider, notifier)

    val queue: StateFlow<List<MangaDownload>> = downloader.queue
    val queueInfo: StateFlow<Map<String, MangaDownloadInfo>> = downloader.queueInfo
    val isRunning: StateFlow<Boolean> = downloader.isRunning

    init {
        // A paused queue must still be visible after the process restarts.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { restoreQueue() }
    }

    /** Adds the given chapters to the queue and starts the worker. */
    suspend fun queueChapters(
        manga: MangaEntry,
        chapters: List<MangaChapter>,
        autoStart: Boolean = true,
    ) {
        if (chapters.isEmpty()) return
        val source = MangaSourceManager.get(app).sourceById(manga.sourceId) as? HttpSource ?: return
        // A title opened straight from a source search has no stored source
        // name yet; the folder is named now, so fill it in before queueing.
        val entry = if (manga.sourceName.isBlank()) manga.copy(sourceName = source.name) else manga
        val downloaded = MangaDownloadCache.downloadedUrls(provider, entry, chapters)
        val queued = downloader.queue.value.mapTo(HashSet()) { it.key }
        val toQueue = chapters
            .filter { it.url !in downloaded && downloadKey(entry, it) !in queued }
            // Mihon queues newest first.
            .sortedByDescending { it.sourceOrder }
            .map { MangaDownload(source, entry, it) }
        downloader.addAll(toQueue)
        if (autoStart && toQueue.isNotEmpty()) start()
    }

    fun start() {
        if (downloader.isRunning.value) return
        MangaDownloadWorker.start(app)
    }

    suspend fun pause() = downloader.pause()

    suspend fun clearQueue() = downloader.clearQueue()

    /** Used by the worker: run the queue in this process. */
    internal suspend fun startDirect(): Boolean = downloader.start()

    suspend fun cancel(chapters: List<MangaChapter>) = downloader.remove(chapters)

    suspend fun retry(chapters: List<MangaChapter>) {
        downloader.retry(chapters)
        start()
    }

    suspend fun isChapterDownloaded(manga: MangaEntry, chapter: MangaChapter): Boolean =
        withContext(Dispatchers.IO) { provider.findChapterDir(manga, chapter) != null }

    suspend fun findChapterDir(manga: MangaEntry, chapter: MangaChapter): UniFile? =
        withContext(Dispatchers.IO) { provider.findChapterDir(manga, chapter) }

    suspend fun downloadedChapterUrls(
        manga: MangaEntry,
        chapters: List<MangaChapter>,
    ): Set<String> = MangaDownloadCache.downloadedUrls(provider, manga, chapters)

    suspend fun deleteChapters(manga: MangaEntry, chapters: List<MangaChapter>) {
        if (chapters.isEmpty()) return
        downloader.remove(chapters)
        withContext(Dispatchers.IO) {
            chapters.forEach { provider.deleteChapter(manga, it) }
        }
        MangaDownloadCache.remove(manga, chapters.map { it.url })
    }

    suspend fun deleteManga(manga: MangaEntry) {
        val queued = downloader.queue.value
            .filter { it.manga.key() == manga.key() }
            .map { it.chapter }
        downloader.remove(queued)
        withContext(Dispatchers.IO) { provider.deleteManga(manga) }
        MangaDownloadCache.invalidate(manga)
    }

    /** Restores the persisted queue; the extensions must be loaded to rebuild it. */
    suspend fun restoreQueue() {
        val records = MangaDownloadStore.restore(app)
        if (records.isEmpty()) return
        val sources = MangaSourceManager.get(app)
        if (sources.sources.value.isEmpty()) {
            val stores = MangaStoreRepository.get(app)
            stores.ensureDefaultStore()
            sources.ensureLoaded { stores.stores.value }
        }
        val downloads = records.mapNotNull { record ->
            val source = sources.sourceById(record.sourceId) as? HttpSource ?: return@mapNotNull null
            MangaDownload(source, record.toManga(), record.toChapter(), record.queuedAt)
        }
        downloader.addAll(downloads)
    }

    companion object {
        @Volatile
        private var instance: MangaDownloadManager? = null

        fun get(context: Context): MangaDownloadManager =
            instance ?: synchronized(this) {
                instance ?: MangaDownloadManager(context).also { instance = it }
            }
    }
}
