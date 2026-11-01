package com.kodastream.app.manga.extension

import android.os.SystemClock
import com.kodastream.app.manga.rootMessage
import eu.kanade.tachiyomi.compat.MangaEngine
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
    val running: Boolean = true,
)

/**
 * The four-step proof that a loaded source actually works: catalogue, details, page list
 * and one image response. Each step reports its own result so a failure names the stage.
 */
class MangaSourceTester(private val engine: MangaEngine.State) {

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
                            add("${manga.title} — ${manga.thumbnail_url ?: "no thumbnail"}")
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
            TestStep.Pass(
                buildList {
                    add("${update.chapters.size} chapters")
                    chapter?.let { add("First: ${it.name}") }
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
        val pages = runStep {
            val list = withTimeout(TIMEOUT_MS) { source.getPageList(firstChapter) }
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
        state = state.copy(image = image, running = false)
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
