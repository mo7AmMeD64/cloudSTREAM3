package com.kodastream.app.manga.reader

import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.readingOrder
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Ported from Mihon's `ui/reader/model/ReaderPage`. The source `Page` keeps
 * its status/progress flows; the reader adds the chapter and the cached file.
 */
class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
) : Page(index, url, imageUrl, null) {

    lateinit var chapter: ReaderChapter

    @Volatile
    var file: File? = null

    /** Decided once while loading, so the reader never sniffs a file on the main thread. */
    @Volatile
    var animated: Boolean = false

    /** Kept for parity with Mihon; the loader points it at [file]. */
    var stream: (() -> InputStream)? = null
}

/**
 * Ported from Mihon's `ReaderChapter`. The domain chapter is KodaStream's own
 * cached [MangaChapter], and the lifetime rules are unchanged.
 */
class ReaderChapter(private val loadedChapter: MangaChapter) {

    var chapter: MangaChapter = loadedChapter
        private set

    val stateFlow = MutableStateFlow<State>(State.Wait)
    var state: State
        get() = stateFlow.value
        set(value) {
            stateFlow.value = value
        }

    val pages: List<ReaderPage>?
        get() = (state as? State.Loaded)?.pages

    var pageLoader: PageLoader? = null

    var requestedPage: Int = 0

    private var references = 0

    fun update(transform: (MangaChapter) -> MangaChapter) {
        chapter = transform(chapter)
    }

    fun ref() {
        references++
    }

    fun unref() {
        references--
        if (references == 0) {
            pageLoader?.recycle()
            pageLoader = null
            state = State.Wait
        }
    }

    sealed interface State {
        data object Wait : State
        data object Loading : State
        data class Error(val error: Throwable) : State
        data class Loaded(val pages: List<ReaderPage>) : State
    }
}

/** Ported from Mihon's `ReaderPage`: a chapter transition at either end. */
sealed class ChapterTransition {
    abstract val from: ReaderChapter
    abstract val to: ReaderChapter?

    class Prev(override val from: ReaderChapter, override val to: ReaderChapter?) : ChapterTransition()
    class Next(override val from: ReaderChapter, override val to: ReaderChapter?) : ChapterTransition()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChapterTransition) return false
        if (from == other.from && to == other.to) return true
        if (from == other.to && to == other.from) return true
        return false
    }

    override fun hashCode(): Int = 31 * from.hashCode() + (to?.hashCode() ?: 0)

    override fun toString(): String =
        "${javaClass.simpleName}(from=${from.chapter.url}, to=${to?.chapter?.url})"
}

/** Ported from Mihon's `ViewerChapters`. */
data class ViewerChapters(
    val currChapter: ReaderChapter,
    val prevChapter: ReaderChapter?,
    val nextChapter: ReaderChapter?,
) {
    fun ref() {
        currChapter.ref()
        prevChapter?.ref()
        nextChapter?.ref()
    }

    fun unref() {
        currChapter.unref()
        prevChapter?.unref()
        nextChapter?.unref()
    }
}

/**
 * Ported from Mihon's `viewerItems`. The previous chapter's pages and its
 * transition come before the current pages, then the next transition and
 * pages, so a swipe across a chapter boundary keeps going.
 */
fun viewerItems(
    chapters: ViewerChapters,
    forceTransition: Boolean,
    currentPages: List<ReaderPage>? = chapters.currChapter.pages,
): MutableList<Any> {
    val items = mutableListOf<Any>()

    val prevHasMissingChapters = chapterGap(chapters.prevChapter, chapters.currChapter) > 0
    val nextHasMissingChapters = chapterGap(chapters.currChapter, chapters.nextChapter) > 0

    chapters.prevChapter?.pages?.let(items::addAll)
    if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
    }

    currentPages?.let(items::addAll)

    if (nextHasMissingChapters || forceTransition || chapters.nextChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(ChapterTransition.Next(chapters.currChapter, chapters.nextChapter))
    }
    chapters.nextChapter?.pages?.let(items::addAll)

    return items
}

/** True when the gap between [from] and [to] should be announced. */
fun chapterGap(from: ReaderChapter?, to: ReaderChapter?): Int {
    if (from == null || to == null) return 0
    val low = from.chapter
    val high = to.chapter
    if (!low.isRecognizedNumber() || !high.isRecognizedNumber()) return 0
    return (high.number - low.number).toInt() - 1
}

private fun MangaChapter.isRecognizedNumber(): Boolean = number >= 0f

/** Chapters in the order the reader shows them. */
fun readerChapterOrder(chapters: List<MangaChapter>): List<MangaChapter> = readingOrder(chapters)

/** One row of the reader's chapter drawer. */
data class ReaderOverlayChapter(
    val index: Int,
    val name: String,
    val number: Float,
    val read: Boolean,
)

/** A menu/overlay snapshot, independent of the views. */
data class ReaderOverlayState(
    val mangaTitle: String = "",
    val chapterName: String = "",
    val chapterUrl: String = "",
    val chapterNumber: Float = -1f,
    val page: Int = 0,
    val pageCount: Int = 0,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val chapters: List<ReaderOverlayChapter> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val noPages: Boolean = false,
    val bookmarked: Boolean = false,
    val menuVisible: Boolean = false,
    val settings: MangaReaderSettings = MangaReaderSettings(),
)

/**
 * Value for the reader slider. The slider is laid out in the reading
 * direction by its caller, so neither the UI language nor the reading mode
 * may flip the value: page 1 is always the first value in reading order.
 * [uiRtl] is part of the contract so all four UI/reading combinations can be
 * tested; the layout direction is what mirrors the track on screen.
 */
fun sliderValueForPage(
    page: Int,
    pageCount: Int,
    readingRtl: Boolean,
    uiRtl: Boolean,
): Float {
    val count = pageCount.coerceAtLeast(1)
    return page.coerceIn(0, count - 1).toFloat()
}

/** Inverse of [sliderValueForPage], clamped to the pages that exist. */
fun pageFromSliderValue(
    value: Float,
    pageCount: Int,
    readingRtl: Boolean,
    uiRtl: Boolean,
): Int {
    val count = pageCount.coerceAtLeast(1)
    return value.roundToInt().coerceIn(0, count - 1)
}
