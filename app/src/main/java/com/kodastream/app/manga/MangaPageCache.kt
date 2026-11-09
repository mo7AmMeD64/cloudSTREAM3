package com.kodastream.app.manga

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

    // Reopening a chapter in the same session must not parse the JSON again.
    private val memo = ConcurrentHashMap<String, CachedPageList>()

    fun get(context: Context, sourceId: Long, chapterUrl: String, now: Long = System.currentTimeMillis()): List<CachedPage>? {
        val cacheKey = key(sourceId, chapterUrl)
        val cached = memo[cacheKey]
            ?: context.getKey<CachedPageList>(FOLDER, cacheKey)?.also { memo[cacheKey] = it }
            ?: return null
        if (cached.pages.isEmpty() || now - cached.fetchedAt > EXPIRY_MS) {
            memo.remove(cacheKey)
            context.removeKey(FOLDER, cacheKey)
            return null
        }
        return cached.pages
    }

    fun save(context: Context, sourceId: Long, chapterUrl: String, pages: List<Page>) {
        val cacheKey = key(sourceId, chapterUrl)
        val value = CachedPageList(
            pages = pages.map { CachedPage(it.url, it.imageUrl) },
            fetchedAt = System.currentTimeMillis(),
        )
        memo[cacheKey] = value
        context.setKey(FOLDER, cacheKey, value)
    }

    /** Drops one chapter so a new request can resolve fresh (often signed) image URLs. */
    fun remove(context: Context, sourceId: Long, chapterUrl: String) {
        val cacheKey = key(sourceId, chapterUrl)
        memo.remove(cacheKey)
        context.removeKey(FOLDER, cacheKey)
    }

    fun clear(context: Context) {
        memo.clear()
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

/** What one page download actually did, for the source tester's diagnostics. */
data class PageFetchInfo(
    /** Null when the file was already on disk and no request was made. */
    val httpCode: Int?,
    val cloudflareSolved: Boolean,
)

/**
 * Fetched page bytes on disk, capped at the size chosen in reader settings.
 * The reader draws from these files through SubsamplingScaleImageView, so a
 * 20000px webtoon page is never a single Java bitmap.
 *
 * The directory is scanned once and then kept in memory, so a page download
 * never walks the whole cache; eviction is amortised instead of running on
 * every page like the old implementation did.
 */
object MangaPageCache {
    // Striped locks: a fixed set cannot leak entries like a per-file map did.
    private val locks = Array(64) { Mutex() }
    private val known = ConcurrentHashMap<String, Long>()
    private val bytes = AtomicLong(0L)

    @Volatile
    private var lastEvict = 0L

    @Volatile
    private var indexed = false

    fun directory(context: Context): File =
        File(context.cacheDir, "manga_pages").apply { mkdirs() }

    fun maxBytes(context: Context): Long = MangaPrefs.readerCacheMb(context) * 1024L * 1024L

    fun usedBytes(context: Context): Long {
        index(context)
        return bytes.get().coerceAtLeast(0L)
    }

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
        known.clear()
        bytes.set(0L)
        indexed = true
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
        onFetched: ((PageFetchInfo) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() }
            ?: source.getImageUrl(page).also { page.imageUrl = it }
        index(context)
        val target = File(directory(context), fileName(source.id, imageUrl))
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(now)
            if (known[target.path] != target.length()) record(target)
            onProgress?.invoke(100)
            onFetched?.invoke(PageFetchInfo(httpCode = null, cloudflareSolved = false))
            return@withContext target
        }
        val lock = locks[(target.path.hashCode() and Int.MAX_VALUE) % locks.size]
        lock.withLock {
            if (target.isFile && target.length() > 0L) {
                onProgress?.invoke(100)
                onFetched?.invoke(PageFetchInfo(httpCode = null, cloudflareSolved = false))
                return@withLock target
            }
            val clearanceBefore = clearance(source, imageUrl)
            val temp = File(target.path + ".tmp")
            source.getImage(page).use { response ->
                if (!response.isSuccessful) {
                    throw HttpException(response.code)
                }
                val body = response.body ?: throw IllegalStateException("Empty page response")
                val total = body.contentLength()
                // -1 means the length is unknown, so the reader shows an
                // indeterminate ring instead of a bar stuck at zero.
                onProgress?.invoke(if (total > 0) 0 else -1)
                var copied = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                // One emit per percent: a per-chunk emit floods
                                // the reader's progress collectors with work.
                                val percent = (100 * copied / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress?.invoke(percent)
                                }
                            }
                        }
                    }
                }
                onFetched?.invoke(
                    PageFetchInfo(
                        httpCode = response.code,
                        cloudflareSolved = clearanceAfterSolve(source, imageUrl, clearanceBefore),
                    ),
                )
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            record(target)
            evictIfNeeded(context, target.path)
            target
        }
    }

    private fun index(context: Context) {
        if (indexed) return
        synchronized(this) {
            if (indexed) return
            var total = 0L
            directory(context).listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".tmp") -> file.delete()
                    file.isFile -> {
                        total += file.length()
                        known[file.path] = file.length()
                    }
                }
            }
            bytes.set(total)
            indexed = true
        }
    }

    private fun record(file: File) {
        val previous = known.put(file.path, file.length())
        if (previous != null) bytes.addAndGet(-previous)
        bytes.addAndGet(file.length())
    }

    private fun evictIfNeeded(context: Context, keep: String) {
        val max = maxBytes(context)
        if (bytes.get() <= max) return
        val now = System.currentTimeMillis()
        // One full scan at most every 30 s; each download after that only
        // touches its own file and the counter.
        synchronized(this) {
            if (now - lastEvict < EVICT_INTERVAL_MS) return
            lastEvict = now
        }
        val files = directory(context).listFiles()
            ?.filter { it.isFile && it.extension == "img" && it.path != keep }
            ?.map { CachedFile(it.path, it.length(), it.lastModified()) }
            .orEmpty()
        val evicted = pageCacheEvictions(files, (max * 9) / 10)
        evicted.forEach { path -> File(path).delete() }
        // Recompute after the pass: the system may also have cleared files
        // behind our back, and a stale counter would evict forever.
        val remaining = directory(context).listFiles()
            ?.filter { it.isFile }
            .orEmpty()
        val present = remaining.mapTo(HashSet()) { it.path }
        known.keys.retainAll(present)
        var total = 0L
        remaining.forEach { file ->
            known[file.path] = file.length()
            total += file.length()
        }
        bytes.set(total)
    }

    private fun clearance(source: HttpSource, url: String): String? = runCatching {
        val httpUrl = url.toHttpUrlOrNull() ?: return@runCatching null
        source.client.cookieJar.loadForRequest(httpUrl)
            .firstOrNull { it.name == "cf_clearance" }
            ?.value
    }.getOrNull()

    private fun clearanceAfterSolve(source: HttpSource, url: String, before: String?): Boolean {
        val after = clearance(source, url) ?: return false
        return after != before
    }

    /** Reuses a cached page when the URL is known without asking the source. */
    fun cached(context: Context, sourceId: Long, imageUrl: String): File? {
        val file = File(directory(context), fileName(sourceId, imageUrl))
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun isCached(context: Context, sourceId: Long, imageUrl: String?): Boolean =
        !imageUrl.isNullOrBlank() && cached(context, sourceId, imageUrl) != null

    fun fileName(sourceId: Long, url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sourceId|$url".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return digest.take(40) + ".img"
    }

    private const val EVICT_INTERVAL_MS = 30_000L
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

/**
 * GIF and animated WebP need an animated drawable instead of a still bitmap.
 * Reads only the header, so it is cheap enough for the page loader's IO pass;
 * the reader never calls it on the main thread.
 */
fun isAnimatedImage(file: File): Boolean = runCatching {
    file.inputStream().use { input ->
        val header = ByteArray(64)
        val count = input.read(header)
        if (count >= 6 &&
            header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
        ) {
            return true
        }
        if (count >= 12 &&
            header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        ) {
            val window = ByteArray(64 * 1024)
            var seen = count
            if (seen > 0) {
                window.copyInto(window, 0, 0, count)
                while (seen < window.size) {
                    val read = input.read(window, seen, window.size - seen)
                    if (read <= 0) break
                    seen += read
                }
                val text = String(window, 0, seen, Charsets.ISO_8859_1)
                return text.contains("ANIM") || text.contains("ANMF")
            }
        }
    }
    false
}.getOrDefault(false)
