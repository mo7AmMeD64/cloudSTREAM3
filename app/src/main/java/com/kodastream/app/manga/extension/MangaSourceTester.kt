package com.kodastream.app.manga.extension

import android.content.Context
import android.os.SystemClock
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaPageCache
import com.kodastream.app.manga.PageFetchInfo
import com.kodastream.app.manga.nameOrNull
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.titleOrEmpty
import com.kodastream.app.manga.urlOrNull
import com.kodastream.app.manga.withRemoteDetails
import eu.kanade.tachiyomi.compat.MangaEngine
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import okhttp3.Request

sealed interface TestStep {
    data object Running : TestStep
    data object Skipped : TestStep
    data class Pass(val lines: List<String>) : TestStep
    data class Fail(val message: String, val detail: String? = null) : TestStep
}

data class MangaSourceTestState(
    val popular: TestStep = TestStep.Running,
    val details: TestStep = TestStep.Skipped,
    val pages: TestStep = TestStep.Skipped,
    val image: TestStep = TestStep.Skipped,
    val pipeline: TestStep = TestStep.Skipped,
    val running: Boolean = true,
)

/**
 * The five-step proof that a loaded source actually works: catalogue, details, page list,
 * one image response and the reader's own three-page download path. Each step reports its
 * own result so a failure names the stage.
 */
class MangaSourceTester(
    private val engine: MangaEngine.State,
    private val context: Context,
) {

    fun run(source: Source): Flow<MangaSourceTestState> = flow {
        var state = MangaSourceTestState()
        suspend fun publish() {
            emit(state)
        }

        val mangas = mutableListOf<SManga>()
        val popular = runStep {
            val started = SystemClock.elapsedRealtime()
            val page = withTimeout(TIMEOUT_MS) { source.getPopularManga(1) }
            val elapsed = SystemClock.elapsedRealtime() - started
            mangas += page.mangas
            if (mangas.isEmpty()) {
                TestStep.Fail("The source returned no manga")
            } else {
                TestStep.Pass(
                    buildList {
                        add("${mangas.size} results in $elapsed ms")
                        mangas.take(3).forEach { manga ->
                            add("${manga.titleOrEmpty()} — ${manga.thumbnail_url ?: "no thumbnail"}")
                        }
                    },
                )
            }
        }
        state = state.copy(popular = popular)
        publish()
        val manga = mangas.firstOrNull()
        if (manga == null) {
            state = state.copy(
                details = TestStep.Fail("No first result to open"),
                running = false,
            )
            publish()
            return@flow
        }

        var chapter: SChapter? = null
        val details = runStep {
            val update = withTimeout(TIMEOUT_MS) {
                source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
            }
            chapter = update.chapters.firstOrNull()
            val local = MangaEntry(
                sourceId = source.id,
                url = manga.urlOrNull().orEmpty(),
                title = manga.titleOrEmpty(),
                thumbnailUrl = manga.thumbnail_url,
            )
            val mapped = local.withRemoteDetails(update.manga, isFavorite = false)
            val remoteUrl = update.manga.urlOrNull()
            val remoteTitle = update.manga.titleOrEmpty()
            TestStep.Pass(
                buildList {
                    add("${update.chapters.size} chapters")
                    chapter?.let { add("First: ${it.nameOrNull() ?: it.urlOrNull() ?: "unnamed"}") }
                    add("url set: ${if (remoteUrl.isNullOrBlank()) "no" else "yes"}")
                    add("title set: ${if (remoteTitle.isBlank()) "no" else "yes"}")
                    add("description: ${update.manga.description?.length ?: 0} chars")
                    add("cover: ${if (update.manga.thumbnail_url.isNullOrBlank()) "no" else "yes"}")
                    add("mapped title: ${mapped.title}")
                },
            )
        }
        state = state.copy(details = details)
        publish()
        val firstChapter = chapter
        if (firstChapter == null) {
            state = state.copy(
                pages = TestStep.Fail("The source returned no chapters"),
                running = false,
            )
            publish()
            return@flow
        }

        var page: Page? = null
        var imageUrl: String? = null
        var pipelinePages: List<Page> = emptyList()
        val pages = runStep {
            val list = withTimeout(TIMEOUT_MS) { source.getPageList(firstChapter) }
            pipelinePages = list.take(3)
            page = list.firstOrNull()
            imageUrl = page?.imageUrl ?: page?.let { p -> (source as? HttpSource)?.getImageUrl(p) }
            TestStep.Pass(
                buildList {
                    add("${list.size} pages")
                    add("First image: ${imageUrl ?: "no image URL"}")
                },
            )
        }
        state = state.copy(pages = pages)
        publish()
        val firstPage = page
        if (firstPage == null || imageUrl.isNullOrBlank()) {
            state = state.copy(
                image = TestStep.Fail("The first page has no image URL"),
                running = false,
            )
            publish()
            return@flow
        }

        val image = runStep {
            val response = if (source is HttpSource) {
                withTimeout(TIMEOUT_MS) { source.getImage(firstPage) }
            } else {
                val request = Request.Builder()
                    .url(imageUrl!!)
                    .header("User-Agent", engine.network?.defaultUserAgentProvider().orEmpty())
                    .build()
                withTimeout(TIMEOUT_MS) { engine.network!!.client.newCall(request).await() }
            }
            response.use {
                TestStep.Pass(
                    listOf(
                        "HTTP ${it.code}",
                        "Content-Type: ${it.header("Content-Type") ?: "not sent"}",
                    ),
                )
            }
        }
        val http = source as? HttpSource
        val pipeline = if (http == null || pipelinePages.isEmpty()) {
            TestStep.Skipped
        } else {
            runStep {
                val lines = coroutineScope {
                    pipelinePages.mapIndexed { index, pipelinePage ->
                        async {
                            val started = SystemClock.elapsedRealtime()
                            var info: PageFetchInfo? = null
                            try {
                                val file = withTimeout(TIMEOUT_MS) {
                                    MangaPageCache.file(
                                        context.applicationContext,
                                        http,
                                        pipelinePage,
                                        onFetched = { info = it },
                                    )
                                }
                                val elapsed = SystemClock.elapsedRealtime() - started
                                val code = info?.httpCode?.let { "HTTP $it" } ?: "cached"
                                "Page ${index + 1}: $code, ${file.length()} bytes, " +
                                    "$elapsed ms, Cloudflare: " +
                                    if (info?.cloudflareSolved == true) "yes" else "no"
                            } catch (e: TimeoutCancellationException) {
                                "Page ${index + 1}: timed out after ${TIMEOUT_MS / 1000} s, " +
                                    "${SystemClock.elapsedRealtime() - started} ms"
                            } catch (e: Throwable) {
                                "Page ${index + 1}: ${rootMessage(e)}, " +
                                    "${SystemClock.elapsedRealtime() - started} ms"
                            }
                        }
                    }.awaitAll()
                }
                TestStep.Pass(lines)
            }
        }
        state = state.copy(image = image, pipeline = pipeline, running = false)
        publish()
    }.flowOn(Dispatchers.IO)

    private suspend fun runStep(block: suspend () -> TestStep): TestStep {
        return try {
            block()
        } catch (e: TimeoutCancellationException) {
            TestStep.Fail("Timed out after ${TIMEOUT_MS / 1000} seconds")
        } catch (e: Throwable) {
            TestStep.Fail(rootMessage(e), e.stackTraceToString())
        }
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
