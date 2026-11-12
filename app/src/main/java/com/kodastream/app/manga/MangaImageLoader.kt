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
                    .maxSizePercent(0.15)
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
                add(MangaCoverFetcher.Factory(manager, context))
                add(MangaPageKeyer())
                add(MangaPageFetcher.Factory(manager, context))
                add(ExtensionIconKeyer())
                add(ExtensionIconFetcher.Factory(context))
            }
            .build()
    }
}

/** Concurrent identical cover requests share one network call. */
private object CoverRequests {
    private val inFlight = HashMap<String, kotlinx.coroutines.CompletableDeferred<ByteArray>>()
    private val lock = Mutex()

    suspend fun load(key: String, block: suspend () -> ByteArray): ByteArray {
        val mine = kotlinx.coroutines.CompletableDeferred<ByteArray>()
        val existing = lock.withLock {
            val current = inFlight[key]
            if (current == null) inFlight[key] = mine
            current
        }
        if (existing != null) return existing.await()
        return try {
            block().also { mine.complete(it) }
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            lock.withLock { inFlight.remove(key) }
        }
    }
}

class MangaCoverKeyer : Keyer<MangaCover> {
    override fun key(data: MangaCover, options: Options): String =
        "manga-cover:${data.sourceId}:${data.url}"
}

class MangaCoverFetcher(
    private val context: Context,
    private val data: MangaCover,
    private val source: HttpSource,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = CoverRequests.load("${data.sourceId}:${data.url}") {
            val request = Request.Builder()
                .url(data.url)
                .headers(source.headers)
                .build()
            source.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body?.bytes() ?: throw IOException("Empty cover response")
            }
        }
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val manager: MangaSourceManager,
        private val context: Context,
    ) : Fetcher.Factory<MangaCover> {
        override fun create(data: MangaCover, options: Options, imageLoader: ImageLoader): Fetcher? {
            val source = manager.sourceById(data.sourceId) as? HttpSource ?: return null
            return MangaCoverFetcher(context, data, source)
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
