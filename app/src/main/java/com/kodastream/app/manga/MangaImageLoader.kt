package com.kodastream.app.manga

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.memory.MemoryCache
import coil.request.Options
import com.kodastream.app.manga.extension.MangaSourceManager
import eu.kanade.tachiyomi.compat.MangaEngine
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/**
 * A second Coil loader for manga only. The video screens keep the app-wide
 * loader untouched; this one knows how to send a source's headers through the
 * source's own OkHttp client, which is what cookie/referer/interceptor-bound
 * covers and pages need. Registered explicitly on manga image calls.
 */
object MangaImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader {
        val manager = MangaSourceManager.get(context)
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "manga_images"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(120)
            .components {
                add(GifDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
                add(MangaCoverKeyer())
                add(MangaCoverFetcher.Factory(manager))
                add(MangaPageKeyer())
                add(MangaPageFetcher.Factory(manager, context))
                add(ExtensionIconKeyer())
                add(ExtensionIconFetcher.Factory(context))
            }
            .build()
    }
}

/**
 * One download per cover at a time. The lock is held across the cache check and
 * the download, so identical requests wait here and then read the finished file
 * from the disk cache instead of downloading it again. Waiting is cancellable,
 * so a card scrolled off screen drops out of the queue.
 */
private object CoverLocks {
    private val stripes = Array(32) { Mutex() }

    suspend fun <T> withLock(key: String, block: suspend () -> T): T =
        stripes[(key.hashCode() and Int.MAX_VALUE) % stripes.size].withLock { block() }
}

class MangaCoverKeyer : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String =
        "manga-cover:${data.sourceId}:${data.url}"
}

/**
 * Covers go through the source's own client, like Mihon's. Unlike a plain
 * Coil fetcher, the bytes are kept in Coil's disk cache, so a cover is
 * downloaded once and a later visit (or a new app start) shows it at once.
 * The call is cancellable: leaving the screen cancels covers still in flight
 * instead of leaving them to block the source's rate limiter.
 */
class MangaCoverFetcher(
    private val data: MangaCover,
    private val source: HttpSource,
    private val options: Options,
    private val diskCache: DiskCache?,
) : Fetcher {
    private val cacheKey = "manga-cover:${data.sourceId}:${data.url}"

    override suspend fun fetch(): FetchResult {
        val policy = options.diskCachePolicy
        val cache = diskCache
        if (cache != null && policy.readEnabled) {
            cache.openSnapshot(cacheKey)?.let { return fromSnapshot(cache, it) }
        }
        return CoverLocks.withLock(cacheKey) {
            if (cache != null && policy.readEnabled) {
                cache.openSnapshot(cacheKey)?.let { return@withLock fromSnapshot(cache, it) }
            }
            download(cache, policy.writeEnabled)
        }
    }

    private suspend fun download(cache: DiskCache?, write: Boolean): FetchResult {
        val request = Request.Builder()
            .url(data.url)
            .headers(source.headers)
            .build()
        return source.client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code)
            val body = response.body ?: throw IOException("Empty cover response")
            if (cache != null && write) {
                val editor = cache.openEditor(cacheKey)
                if (editor != null) {
                    try {
                        cache.fileSystem.write(editor.data) { writeAll(body.source()) }
                        // The body is spent once it is written, so the cache entry is
                        // the only copy left to return.
                        val snapshot = editor.commitAndOpenSnapshot()
                            ?: cache.openSnapshot(cacheKey)
                            ?: throw IOException("Cover cache write failed")
                        return@use fromSnapshot(cache, snapshot)
                    } catch (e: Throwable) {
                        runCatching { editor.abort() }
                        throw e
                    }
                }
            }
            // No usable disk cache: hand the bytes over from memory.
            SourceResult(
                source = ImageSource(Buffer().write(body.bytes()), options.context),
                mimeType = null,
                dataSource = DataSource.NETWORK,
            )
        }
    }

    private fun fromSnapshot(cache: DiskCache, snapshot: DiskCache.Snapshot): FetchResult =
        SourceResult(
            source = ImageSource(
                file = snapshot.data,
                fileSystem = cache.fileSystem,
                diskCacheKey = cacheKey,
                closeable = snapshot,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

    class Factory(
        private val manager: MangaSourceManager,
    ) : Fetcher.Factory<MangaCover> {
        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher? {
            val source = manager.sourceById(data.sourceId) as? HttpSource ?: return null
            return MangaCoverFetcher(data, source, options, imageLoader.diskCache)
        }
    }
}

class MangaPageKeyer : Keyer<MangaPage> {
    override fun key(data: MangaPage, options: Options): String =
        "manga-page:${data.sourceId}:${data.page.imageUrl ?: data.page.url}"
}

/**
 * Coil route for pages. The reader draws static pages straight from the file
 * cache through SubsamplingScaleImageView (large images stay tiled); this
 * fetcher is used for animated pages and anywhere a Coil page is requested.
 */
class MangaPageFetcher(
    private val context: Context,
    private val data: MangaPage,
    private val source: HttpSource,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val file = MangaPageCache.file(context, source, data.page)
        return SourceResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val manager: MangaSourceManager,
        private val context: Context,
    ) : Fetcher.Factory<MangaPage> {
        override fun create(data: MangaPage, options: Options, imageLoader: ImageLoader): Fetcher? {
            val source = manager.sourceById(data.sourceId) as? HttpSource ?: return null
            return MangaPageFetcher(context, data, source)
        }
    }
}

class ExtensionIconKeyer : Keyer<MangaExtensionIcon> {
    override fun key(data: MangaExtensionIcon, options: Options): String =
        "manga-icon:${data.url}"
}

class ExtensionIconFetcher(
    private val context: Context,
    private val data: MangaExtensionIcon,
    private val client: OkHttpClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val request = Request.Builder().url(data.url).build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty icon response")
        }
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<MangaExtensionIcon> {
        override fun create(
            data: MangaExtensionIcon,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            val client = MangaEngine.get(context).network?.client ?: OkHttpClient()
            return ExtensionIconFetcher(context, data, client)
        }
    }
}
