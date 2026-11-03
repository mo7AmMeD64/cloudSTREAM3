package com.kodastream.app.manga

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** One cached page URL; the image itself lives in the page byte cache. */
data class CachedPage(
    val url: String = "",
    val imageUrl: String? = null,
)

data class CachedPageList(
    val pages: List<CachedPage> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * Page lists are cheap to fetch but expensive to lose on a slow site. Kept
 * for three hours, like Mihon's chapter cache expiry, in their own folder.
 */
object MangaPageListStore {
    internal const val FOLDER = "manga_page_lists"
    const val EXPIRY_MS = 3L * 60L * 60L * 1000L

    fun get(context: Context, sourceId: Long, chapterUrl: String, now: Long = System.currentTimeMillis()): List<CachedPage>? {
        val cached = context.getKey<CachedPageList>(FOLDER, key(sourceId, chapterUrl)) ?: return null
        if (cached.pages.isEmpty() || now - cached.fetchedAt > EXPIRY_MS) return null
        return cached.pages
    }

    fun save(context: Context, sourceId: Long, chapterUrl: String, pages: List<Page>) {
        context.setKey(
            FOLDER,
            key(sourceId, chapterUrl),
            CachedPageList(
                pages = pages.map { CachedPage(it.url, it.imageUrl) },
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun clear(context: Context) {
        context.removeKeys(FOLDER)
    }

    private fun key(sourceId: Long, chapterUrl: String) = "$sourceId|$chapterUrl"
}

data class CachedFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
)

/** Oldest files first until the cache fits; pure so the eviction is testable. */
fun pageCacheEvictions(files: List<CachedFile>, maxBytes: Long): List<String> {
    var total = files.sumOf { it.size }
    if (total <= maxBytes) return emptyList()
    val evicted = mutableListOf<String>()
    for (file in files.sortedBy { it.lastModified }) {
        if (total <= maxBytes) break
        evicted.add(file.path)
        total -= file.size
    }
    return evicted
}

/**
 * Fetched page bytes on disk, capped at the size chosen in reader settings.
 * The reader draws from these files through SubsamplingScaleImageView, so a
 * 20000px webtoon page is never a single Java bitmap.
 */
object MangaPageCache {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun directory(context: Context): File =
        File(context.cacheDir, "manga_pages").apply { mkdirs() }

    fun maxBytes(context: Context): Long = MangaPrefs.readerCacheMb(context) * 1024L * 1024L

    fun usedBytes(context: Context): Long =
        directory(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Downloads [page] through the source's own client, reusing the file when
     * it exists. Concurrent requests for one page share a lock, so a page is
     * never downloaded twice. [onProgress] is the source's progress callback.
     */
    suspend fun file(
        context: Context,
        source: HttpSource,
        page: Page,
        now: Long = System.currentTimeMillis(),
        onProgress: ((Int) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() }
            ?: source.getImageUrl(page).also { page.imageUrl = it }
        val target = File(directory(context), fileName(source.id, imageUrl))
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(now)
            onProgress?.invoke(100)
            return@withContext target
        }
        val lock = locks.getOrPut(target.path) { Mutex() }
        lock.withLock {
            if (target.isFile && target.length() > 0L) return@withLock target
            val temp = File(target.path + ".tmp")
            source.getImage(page).use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code)
                }
                val body = response.body ?: throw IllegalStateException("Empty page response")
                val total = body.contentLength()
                var copied = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress?.invoke((100 * copied / total).toInt())
                        }
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            evict(context)
            target
        }
    }

    /** Reuses a cached page when the URL is known without asking the source. */
    fun cached(context: Context, sourceId: Long, imageUrl: String): File? {
        val file = File(directory(context), fileName(sourceId, imageUrl))
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun fileName(sourceId: Long, url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sourceId|$url".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return digest.take(40) + ".img"
    }

    private fun evict(context: Context) {
        val files = directory(context).listFiles()
            ?.filter { it.isFile && it.extension == "img" }
            ?.map { CachedFile(it.path, it.length(), it.lastModified()) }
            .orEmpty()
        pageCacheEvictions(files, maxBytes(context)).forEach { path -> File(path).delete() }
    }
}

/** A cover request that must go through the source's client and headers. */
data class MangaCover(
    val sourceId: Long,
    val url: String,
    val thumbnailUrl: String? = null,
)

/** A page request for animated/small images; static reader pages use the file cache. */
data class MangaPage(
    val sourceId: Long,
    val chapterUrl: String,
    val index: Int,
    val page: Page,
)

/** An extension's store icon URL. */
data class MangaExtensionIcon(
    val url: String,
)
