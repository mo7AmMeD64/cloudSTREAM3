// Ported from Mihon's data/download/model/Download.kt (Apache-2.0),
// adapted to KodaStream's manga models.
package com.kodastream.app.manga.download

import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.milliseconds

/** One chapter waiting in, or passing through, the download queue. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
data class MangaDownload(
    val source: HttpSource,
    val manga: MangaEntry,
    val chapter: MangaChapter,
    val queuedAt: Long = System.currentTimeMillis(),
) {
    val key: String get() = downloadKey(manga, chapter)

    private val _pagesFlow = MutableStateFlow<List<Page>?>(null)
    val pagesFlow = _pagesFlow.asStateFlow()
    var pages: List<Page>?
        get() = _pagesFlow.value
        set(value) {
            _pagesFlow.value = value
        }

    val downloadedImages: Int
        get() = pages?.count { it.status == Page.State.Ready } ?: 0

    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    val progressFlow = _pagesFlow
        .flatMapLatest { pages ->
            if (pages == null) {
                flowOf(0)
            } else {
                combine(pages.map(Page::progressFlow)) { it.average().toInt() }
            }
        }
        .distinctUntilChanged()
        .debounce(50.milliseconds)

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().toInt()
        }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }
}

fun downloadKey(manga: MangaEntry, chapter: MangaChapter): String =
    "${manga.sourceId}|${manga.url}|${chapter.url}"
