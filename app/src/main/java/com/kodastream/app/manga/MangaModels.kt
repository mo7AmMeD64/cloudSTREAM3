package com.kodastream.app.manga

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max

/** The smallest thing every manga screen needs to open a title before anything is loaded. */
data class MangaRef(
    val sourceId: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
)

/**
 * One title in the manga library. Plain Jackson-friendly values only: it is
 * written through CloudStream's DataStore, like the video library entries.
 */
data class MangaEntry(
    val sourceId: Long = 0L,
    val url: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: Int = 0,
    /** SManga.UpdateStrategy name, so an "only fetch once" title can skip updates. */
    val updateStrategy: String = "ALWAYS_UPDATE",
    /** The source's opaque extra metadata, kept as JSON text. */
    val memo: String = "",
    val sourceName: String = "",
    val language: String = "",
    val addedAt: Long = 0L,
    val lastReadAt: Long = 0L,
    val lastChecked: Long = 0L,
)

/** A cached chapter row, in the order the source returned it. */
data class MangaChapter(
    val url: String = "",
    val name: String = "",
    val number: Float = -1f,
    val scanlator: String? = null,
    val dateUpload: Long = 0L,
    val sourceOrder: Int = 0,
    /** The source's opaque extra metadata, kept as JSON text. */
    val memo: String = "",
)

data class MangaChapterList(
    val chapters: List<MangaChapter> = emptyList(),
)

/** Reading state for one chapter; [lastPageRead] is a zero-based page index. */
data class ChapterProgress(
    val read: Boolean = false,
    val lastPageRead: Int = 0,
    val pageCount: Int = 0,
    val bookmarked: Boolean = false,
    val readAt: Long = 0L,
)

/** A progress row plus the keys needed to find it again. */
data class MangaProgressRecord(
    val sourceId: Long = 0L,
    val mangaUrl: String = "",
    val chapterUrl: String = "",
    val progress: ChapterProgress = ChapterProgress(),
)

/** The last chapter read for one title, newest first in the history screen. */
data class MangaHistoryEntry(
    val sourceId: Long = 0L,
    val mangaUrl: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    val chapterUrl: String = "",
    val chapterName: String = "",
    val chapterNumber: Float = -1f,
    val page: Int = 0,
    val pageCount: Int = 0,
    val readAt: Long = 0L,
)

/** Library sorting, all choices remembered in [MangaPrefs]. */
enum class MangaLibrarySort {
    TITLE,
    LAST_READ,
    LAST_UPDATED,
    DATE_ADDED,
    UNREAD,
    TOTAL_CHAPTERS,
}

data class MangaLibraryFilter(
    val unreadOnly: Boolean = false,
    val startedOnly: Boolean = false,
    val completedOnly: Boolean = false,
    val sourceName: String? = null,
    val language: String? = null,
)

/** Where the reader should open a title: a chapter and a zero-based page. */
data class ContinueReading(
    val chapter: MangaChapter,
    val page: Int,
    val allRead: Boolean = false,
)

/** A gap between two recognized chapter numbers. */
data class ChapterGap(
    val afterNumber: Float,
    val count: Int,
)

data class HistoryGroup(
    val dayStart: Long,
    val entries: List<MangaHistoryEntry>,
)

fun mangaKey(sourceId: Long, url: String): String = "$sourceId|$url"

fun MangaRef.key(): String = mangaKey(sourceId, url)

fun MangaEntry.key(): String = mangaKey(sourceId, url)

fun MangaHistoryEntry.key(): String = mangaKey(sourceId, mangaUrl)

fun progressKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
    "$sourceId|$mangaUrl|$chapterUrl"

fun MangaChapter.isRecognizedNumber(): Boolean = number >= 0f

/**
 * Mihon's chapter gap rule: only whole numbers count, and a chapter 2.5 after
 * 1 does not leave a hole. Returns 0 when either side is unnumbered.
 */
fun chapterGap(higher: Float, lower: Float): Int {
    if (higher < 0f || lower < 0f) return 0
    return max(0, floor(higher).toInt() - floor(lower).toInt() - 1)
}

/** Every whole-number hole in a chapter list, ascending, duplicates ignored. */
fun chapterGaps(chapters: List<MangaChapter>): List<ChapterGap> {
    val numbers = chapters
        .filter { it.isRecognizedNumber() }
        .map { floor(it.number).toInt() }
        .distinct()
        .sorted()
    if (numbers.size < 2) return emptyList()
    return numbers.zipWithNext().mapNotNull { (low, high) ->
        val missing = high - low - 1
        if (missing > 0) ChapterGap(low.toFloat(), missing) else null
    }
}

/** Chapters in reading order: number ascending, then source order. */
fun readingOrder(chapters: List<MangaChapter>): List<MangaChapter> =
    chapters.sortedWith(
        compareBy(
            { if (it.isRecognizedNumber()) it.number else Float.MAX_VALUE },
            { it.sourceOrder },
        )
    )

fun nextChapter(chapters: List<MangaChapter>, currentUrl: String): MangaChapter? {
    val ordered = readingOrder(chapters)
    val index = ordered.indexOfFirst { it.url == currentUrl }
    return if (index >= 0) ordered.getOrNull(index + 1) else null
}

fun previousChapter(chapters: List<MangaChapter>, currentUrl: String): MangaChapter? {
    val ordered = readingOrder(chapters)
    val index = ordered.indexOfFirst { it.url == currentUrl }
    return if (index > 0) ordered[index - 1] else null
}

/**
 * The details screen's Continue button. An in-progress chapter wins, then the
 * oldest unread chapter in source order (Mihon's `getNextUnread`), then the
 * first chapter when everything is already read.
 */
fun continueReading(
    chapters: List<MangaChapter>,
    progress: Map<String, ChapterProgress>,
): ContinueReading? {
    if (chapters.isEmpty()) return null
    val inProgress = chapters
        .mapNotNull { chapter ->
            val state = progress[chapter.url] ?: return@mapNotNull null
            if (!state.read && state.lastPageRead > 0) chapter to state else null
        }
        .maxByOrNull { it.second.readAt }
    if (inProgress != null) {
        return ContinueReading(inProgress.first, inProgress.second.lastPageRead)
    }
    val ordered = readingOrder(chapters)
    val unread = ordered.firstOrNull { progress[it.url]?.read != true }
    if (unread != null) return ContinueReading(unread, 0)
    return ContinueReading(ordered.first(), 0, allRead = true)
}

/** Unread chapters in a title, using read flags only. */
fun unreadCount(chapters: List<MangaChapter>, progress: Map<String, ChapterProgress>): Int =
    chapters.count { progress[it.url]?.read != true }

/** Drops duplicate chapters by URL first, then by number + scanlator. */
fun dedupeChapters(chapters: List<MangaChapter>): List<MangaChapter> {
    val seenUrls = HashSet<String>()
    val seenNumbers = HashSet<String>()
    return chapters.filter { chapter ->
        if (!seenUrls.add(chapter.url)) return@filter false
        if (!chapter.isRecognizedNumber()) return@filter true
        if (!seenNumbers.add(numberKey(chapter))) return@filter false
        true
    }
}

private fun numberKey(chapter: MangaChapter): String =
    "${chapter.number}|${chapter.scanlator.orEmpty().trim().lowercase()}"

/**
 * The outcome of syncing a fetched chapter list with the cached one. [aliases]
 * maps a new chapter URL to the old URL when the source changed only the link,
 * so the caller can move the read flags forward without losing them.
 */
data class ChapterSyncResult(
    val chapters: List<MangaChapter>,
    val aliases: Map<String, String>,
)

/**
 * Keeps chapter order, read flags and numbers stable across source changes.
 * A chapter whose URL changed is matched by number and scanlator; a renamed
 * chapter with the same URL stays the same row. Old chapters that disappeared
 * from the source are not deleted here; their progress stays in the store.
 */
fun syncChapters(cached: List<MangaChapter>, fetched: List<MangaChapter>): ChapterSyncResult {
    val usedOld = HashSet<String>()
    val aliases = HashMap<String, String>()
    val byUrl = cached.associateBy { it.url }
    val result = fetched.mapIndexed { index, chapter ->
        val sameUrl = byUrl[chapter.url]
        if (sameUrl != null) {
            usedOld.add(sameUrl.url)
            return@mapIndexed chapter.copy(sourceOrder = index)
        }
        val match = cached.firstOrNull { old ->
            old.url !in usedOld &&
                old.isRecognizedNumber() &&
                chapter.isRecognizedNumber() &&
                old.number == chapter.number &&
                scanlatorKey(old) == scanlatorKey(chapter)
        }
        if (match != null) {
            usedOld.add(match.url)
            aliases[chapter.url] = match.url
        }
        chapter.copy(sourceOrder = index)
    }
    return ChapterSyncResult(dedupeChapters(result), aliases)
}

private fun scanlatorKey(chapter: MangaChapter): String =
    chapter.scanlator.orEmpty().trim().lowercase()

/** Pagination merge: keep the old order, append only URLs not seen yet. */
fun <T> mergeUnique(
    existing: List<T>,
    incoming: List<T>,
    key: (T) -> String,
): List<T> {
    if (incoming.isEmpty()) return existing
    val seen = existing.mapTo(HashSet(), key)
    return existing + incoming.filter { seen.add(key(it)) }
}

/** Library filtering. [started] holds keys with at least one read chapter. */
fun filterLibrary(
    entries: List<MangaEntry>,
    filter: MangaLibraryFilter,
    started: Set<String>,
    unread: Map<String, Int>,
): List<MangaEntry> = entries.filter { entry ->
    val key = entry.key()
    (!filter.unreadOnly || (unread[key] ?: 0) > 0) &&
        (!filter.startedOnly || key in started) &&
        (!filter.completedOnly || entry.status == 2) &&
        (filter.sourceName == null || entry.sourceName == filter.sourceName) &&
        (filter.language == null || entry.language == filter.language)
}

/**
 * Library sorting. [lastUpdated] is the newest chapter upload per title and
 * [totals] the chapter count; missing values sort last.
 */
fun sortLibrary(
    entries: List<MangaEntry>,
    sort: MangaLibrarySort,
    descending: Boolean,
    unread: Map<String, Int>,
    totals: Map<String, Int>,
    lastUpdated: Map<String, Long>,
): List<MangaEntry> {
    val comparator: Comparator<MangaEntry> = when (sort) {
        MangaLibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        MangaLibrarySort.LAST_READ -> compareBy { it.lastReadAt }
        MangaLibrarySort.LAST_UPDATED -> compareBy { lastUpdated[it.key()] ?: Long.MIN_VALUE }
        MangaLibrarySort.DATE_ADDED -> compareBy { it.addedAt }
        MangaLibrarySort.UNREAD -> compareBy { unread[it.key()] ?: 0 }
        MangaLibrarySort.TOTAL_CHAPTERS -> compareBy { totals[it.key()] ?: 0 }
    }
    return if (descending) entries.sortedWith(comparator.reversed()) else entries.sortedWith(comparator)
}

/** Newest chapter upload for a title, or 0 when the cache is empty. */
fun newestChapterDate(chapters: List<MangaChapter>): Long =
    chapters.maxOfOrNull { it.dateUpload } ?: 0L

/** Newest first, grouped by local calendar day. */
fun groupHistory(
    entries: List<MangaHistoryEntry>,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): List<HistoryGroup> {
    val groups = LinkedHashMap<Long, MutableList<MangaHistoryEntry>>()
    entries.sortedByDescending { it.readAt }.forEach { entry ->
        val day = Instant.ofEpochMilli(entry.readAt).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        groups.getOrPut(start) { mutableListOf() }.add(entry)
    }
    return groups.map { (dayStart, list) -> HistoryGroup(dayStart, list) }
}

/** A page saved into progress is always a valid zero-based page. */
fun clampPage(page: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)

/** Progress is written at most once per interval while the reader is open. */
fun shouldPersistProgress(
    lastSavedAt: Long,
    now: Long,
    intervalMs: Long = 3_000L,
): Boolean = now - lastSavedAt >= intervalMs

/** The last page shown marks the chapter as read. */
fun isLastPage(page: Int, pageCount: Int): Boolean =
    pageCount > 0 && page >= pageCount - 1

// ------------------------------------------------------------------ library categories

/**
 * The manga-side counterpart of the video category filter. Kept pure so a
 * test can prove categories never leak into the video folders.
 */
fun <T> itemsInCategory(
    items: List<T>,
    mappings: Map<String, String>,
    validCategoryIds: Set<String>,
    categoryId: String?,
    keyOf: (T) -> String,
): List<T> {
    val effective = mappings.filterValues { it in validCategoryIds }
    return items.filter { effective[keyOf(it)] == categoryId }
}

// ------------------------------------------------------------------ reader navigation

enum class TapZoneLayout {
    RIGHT_AND_LEFT,
    L_SHAPED,
    KINDLISH,
    EDGE,
    DISABLED,
}

enum class TapInvert {
    NONE,
    HORIZONTAL,
    VERTICAL,
    BOTH,
}

enum class TapAction {
    MENU,
    FORWARD,
    BACKWARD,
}

data class TapRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val action: TapAction,
) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < right && y >= top && y < bottom
}

/**
 * Mihon's five tap layouts. [readingRtl] only swaps the left and right
 * regions; horizontal inversion is a user setting on top of that.
 */
fun tapRegions(
    layout: TapZoneLayout,
    invert: TapInvert = TapInvert.NONE,
    readingRtl: Boolean = true,
): List<TapRegion> {
    val horizontal = invert == TapInvert.HORIZONTAL || invert == TapInvert.BOTH
    val vertical = invert == TapInvert.VERTICAL || invert == TapInvert.BOTH

    fun region(l: Float, t: Float, r: Float, b: Float, action: TapAction): TapRegion {
        val left = if (horizontal) 1f - r else l
        val right = if (horizontal) 1f - l else r
        val top = if (vertical) 1f - b else t
        val bottom = if (vertical) 1f - t else b
        return TapRegion(left, top, right, bottom, action)
    }

    fun side(sideLeft: Boolean, action: TapAction): TapAction {
        if (action != TapAction.FORWARD && action != TapAction.BACKWARD) return action
        val onLeft = sideLeft
        return when {
            readingRtl && onLeft -> TapAction.FORWARD
            readingRtl -> TapAction.BACKWARD
            onLeft -> TapAction.BACKWARD
            else -> TapAction.FORWARD
        }
    }

    return when (layout) {
        TapZoneLayout.DISABLED -> emptyList()
        TapZoneLayout.RIGHT_AND_LEFT -> listOf(
            region(0f, 0f, 0.33f, 1f, side(true, TapAction.BACKWARD)),
            region(0.66f, 0f, 1f, 1f, side(false, TapAction.FORWARD)),
        )
        TapZoneLayout.EDGE -> listOf(
            region(0f, 0f, 0.33f, 1f, TapAction.FORWARD),
            region(0.33f, 0.66f, 0.66f, 1f, TapAction.BACKWARD),
            region(0.66f, 0f, 1f, 1f, TapAction.FORWARD),
        )
        TapZoneLayout.KINDLISH -> listOf(
            region(0.33f, 0.33f, 1f, 1f, TapAction.FORWARD),
            region(0f, 0.33f, 0.33f, 1f, TapAction.BACKWARD),
        )
        TapZoneLayout.L_SHAPED -> listOf(
            region(0f, 0.33f, 0.33f, 0.66f, TapAction.BACKWARD),
            region(0f, 0f, 1f, 0.33f, TapAction.BACKWARD),
            region(0.66f, 0.33f, 1f, 0.66f, TapAction.FORWARD),
            region(0f, 0.66f, 1f, 1f, TapAction.FORWARD),
        )
    }
}

fun tapAction(
    x: Float,
    y: Float,
    layout: TapZoneLayout,
    invert: TapInvert = TapInvert.NONE,
    readingRtl: Boolean = true,
): TapAction = tapRegions(layout, invert, readingRtl)
    .firstOrNull { it.contains(x, y) }
    ?.action
    ?: TapAction.MENU
