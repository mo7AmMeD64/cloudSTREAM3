package com.kodastream.app.manga.reader

import com.kodastream.app.manga.CachedPage
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.PageFetchInfo
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Section 4d: the chunk/cancel path is what left displayed pages in Queue. */
class HttpPageLoaderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (!path.startsWith("/p/")) return MockResponse().setResponseCode(404)
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("image bytes for $path")
                    .setBodyDelay(20, TimeUnit.MILLISECONDS)
            }
        }
        server.start()
        client = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun quickSuccessiveLoadsReachReadyAndNeverStayQueued() = runBlocking {
        val pages = loadSevenPages()
        val loader = chapterLoader(pages)

        val loads = pages.map { page -> launch(Dispatchers.IO) { loader.loadPage(page) } }
        awaitTerminal(pages)
        loads.joinAll()
        loader.recycle()

        assertEquals(
            "all pages should load: " + pages.joinToString { "#${it.index}=${it.status}" },
            pages.size,
            pages.count { it.status is Page.State.Ready },
        )
        assertTrue(
            "a page stayed in Queue: " + pages.joinToString { "#${it.index}=${it.status}" },
            pages.none { it.status is Page.State.Queue },
        )
    }

    @Test
    fun oneChunkPassLeavesNoPageQueued() = runBlocking {
        val pages = loadSevenPages()
        val loader = chapterLoader(pages)

        loader.loadPage(pages[3])
        awaitTerminal(pages)
        loader.recycle()

        assertTrue(
            "a page stayed in Queue: " + pages.joinToString { "#${it.index}=${it.status}" },
            pages.none { it.status is Page.State.Queue },
        )
        assertEquals(
            pages.size,
            pages.count { it.status is Page.State.Ready },
        )
    }

    private suspend fun loadSevenPages(): List<ReaderPage> {
        val chapter = ReaderChapter(MangaChapter(url = "/chapter"))
        val source = FakeSource(server.url("/").toString(), client)
        val cached = (0 until 7).map { index ->
            CachedPage(
                url = "p$index",
                imageUrl = server.url("/p/$index").toString(),
            )
        }
        val loader = HttpPageLoader(chapter, source, NetworkStore(cached))
        chapter.pageLoader = loader
        val pages = loader.getPages().onEach { it.chapter = chapter }
        // ChapterLoader publishes this once getPages() returns; loadPage() plans
        // against it, so tests must set it too.
        chapter.state = ReaderChapter.State.Loaded(pages)
        return pages
    }

    private fun chapterLoader(pages: List<ReaderPage>): HttpPageLoader =
        pages.first().chapter.pageLoader as HttpPageLoader

    private suspend fun awaitTerminal(pages: List<ReaderPage>) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (pages.all { it.status is Page.State.Ready || it.status is Page.State.Error }) return
            delay(20)
        }
        throw AssertionError(
            "pages did not settle: " + pages.joinToString { "#${it.index}=${it.status}" },
        )
    }

    /** Cached page list plus a real HTTP fetch, so the MockWebServer drives the loader. */
    private class NetworkStore(private val cached: List<CachedPage>) : ReaderPageStore {
        override suspend fun pageList(sourceId: Long, chapterUrl: String): List<CachedPage> = cached

        override suspend fun savePageList(
            sourceId: Long,
            chapterUrl: String,
            pages: List<Page>,
        ) = Unit

        override suspend fun invalidatePageList(sourceId: Long, chapterUrl: String) = Unit

        override suspend fun file(
            source: HttpSource,
            page: ReaderPage,
            onProgress: (Int) -> Unit,
            onFetched: (PageFetchInfo) -> Unit,
        ): File {
            source.getImage(page).use { response ->
                onProgress(0)
                val file = File.createTempFile("koda-page-", ".img")
                file.deleteOnExit()
                file.writeBytes(response.body!!.bytes())
                onProgress(100)
                onFetched(PageFetchInfo(response.code, cloudflareSolved = false))
                return file
            }
        }
    }

    private class FakeSource(
        override val baseUrl: String,
        private val http: OkHttpClient,
    ) : HttpSource() {
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override val client: OkHttpClient get() = http

        override fun headersBuilder(): Headers.Builder = Headers.Builder()

        override suspend fun getPopularManga(page: Int): MangasPage =
            throw UnsupportedOperationException()

        override suspend fun getLatestUpdates(page: Int): MangasPage =
            throw UnsupportedOperationException()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = throw UnsupportedOperationException()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            throw UnsupportedOperationException()
    }
}
