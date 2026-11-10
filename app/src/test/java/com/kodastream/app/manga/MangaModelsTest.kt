package com.kodastream.app.manga

import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.data.FavoriteCategoryLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MangaModelsTest {

    @Test
    fun pageListKeyKeepsSlugOnlyChaptersOfDifferentMangaApart() {
        val first = MangaChapter(url = "chapter-1", memo = """{"mangaPath":"/manga/a/"}""")
        val second = MangaChapter(url = "chapter-1", memo = """{"mangaPath":"/manga/b/"}""")
        assertTrue(first.pageListKey() != second.pageListKey())
        assertEquals(first.pageListKey(), first.copy().pageListKey())
    }

    @Test
    fun pageListKeyIsTheUrlWhenThereIsNoMemo() {
        assertEquals("/manga/a/chapter-1", MangaChapter(url = "/manga/a/chapter-1").pageListKey())
        assertEquals("/manga/a/chapter-1", MangaChapter(url = "/manga/a/chapter-1", memo = "{}").pageListKey())
    }

    private fun chapter(
        url: String,
        number: Float,
        order: Int = 0,
        scanlator: String? = null,
    ) = MangaChapter(
        url = url,
        name = "Ch $number",
        number = number,
        sourceOrder = order,
        scanlator = scanlator,
    )

    private fun entry(
        url: String,
        title: String = url,
        status: Int = 0,
        source: String = "Source",
        language: String = "en",
        addedAt: Long = 0L,
        lastReadAt: Long = 0L,
    ) = MangaEntry(
        sourceId = 1L,
        url = url,
        title = title,
        status = status,
        sourceName = source,
        language = language,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
    )

    // ---------------------------------------------------------------- pagination

    @Test
    fun paginationKeepsOrderAndDropsSeenUrls() {
        val existing = listOf("a", "b")
        val incoming = listOf("b", "c", "c", "a", "d")

        assertEquals(
            listOf("a", "b", "c", "d"),
            mergeUnique(existing, incoming) { it },
        )
    }

    @Test
    fun emptyPageKeepsListUntouched() {
        val existing = listOf("a", "b")

        assertEquals(existing, mergeUnique(existing, emptyList<String>()) { it })
    }

    // ---------------------------------------------------------------- reading order

    @Test
    fun readingOrderSortsByNumberThenSourceOrderWithUnknownLast() {
        val chapters = listOf(
            chapter("c", 2f, 0),
            chapter("a-late", 1f, 2),
            chapter("a-early", 1f, 1),
            chapter("unknown", -1f, 0),
        )

        assertEquals(
            listOf("a-early", "a-late", "c", "unknown"),
            readingOrder(chapters).map { it.url },
        )
    }

    @Test
    fun nextAndPreviousFollowReadingOrder() {
        val chapters = listOf(chapter("b", 2f), chapter("a", 1f), chapter("c", 3f))

        assertEquals("b", nextChapter(chapters, "a")?.url)
        assertEquals("c", nextChapter(chapters, "b")?.url)
        assertNull(nextChapter(chapters, "c"))
        assertEquals("a", previousChapter(chapters, "b")?.url)
        assertNull(previousChapter(chapters, "a"))
        assertNull(nextChapter(chapters, "missing"))
    }

    @Test
    fun chaptersBeforeFollowsReadingOrderAndStopsAtTheCurrentChapter() {
        val chapters = listOf(chapter("c", 3f), chapter("a", 1f), chapter("b", 2f))

        assertEquals(
            listOf("a", "b"),
            chaptersBefore(chapters, "c").map { it.url },
        )
        assertTrue(chaptersBefore(chapters, "a").isEmpty())
        assertTrue(chaptersBefore(chapters, "missing").isEmpty())
    }

    @Test
    fun continuePrefersTheNewestInProgressChapter() {
        val chapters = listOf(chapter("a", 1f), chapter("b", 2f), chapter("c", 3f))
        val progress = mapOf(
            "a" to ChapterProgress(read = true, lastPageRead = 4, readAt = 50L),
            "b" to ChapterProgress(lastPageRead = 2, readAt = 10L),
            "c" to ChapterProgress(lastPageRead = 5, readAt = 30L),
        )

        val target = continueReading(chapters, progress)!!

        assertEquals("c", target.chapter.url)
        assertEquals(5, target.page)
        assertFalse(target.allRead)
    }

    @Test
    fun continueFallsBackToTheFirstUnreadChapter() {
        val chapters = listOf(chapter("b", 2f), chapter("a", 1f), chapter("c", 3f))
        val progress = mapOf("a" to ChapterProgress(read = true), "b" to ChapterProgress())

        val target = continueReading(chapters, progress)!!

        assertEquals("b", target.chapter.url)
        assertEquals(0, target.page)
    }

    @Test
    fun continueMarksEverythingReadAtTheFirstChapter() {
        val chapters = listOf(chapter("a", 1f), chapter("b", 2f))
        val progress = chapters.associate { it.url to ChapterProgress(read = true) }

        val target = continueReading(chapters, progress)!!

        assertEquals("a", target.chapter.url)
        assertTrue(target.allRead)
    }

    @Test
    fun emptyTitleHasNothingToContinue() {
        assertNull(continueReading(emptyList(), emptyMap()))
    }

    // ---------------------------------------------------------------- gaps and dedupe

    @Test
    fun gapsOnlyCountWholeMissingNumbers() {
        assertEquals(1, chapterGap(3f, 1f))
        assertEquals(0, chapterGap(2.5f, 1f))
        assertEquals(0, chapterGap(-1f, 1f))
        assertEquals(0, chapterGap(2f, 3f))
    }

    @Test
    fun chapterGapsAreAscendingAndIgnoreFractionsAndDuplicates() {
        val chapters = listOf(
            chapter("one", 1f),
            chapter("two-and-a-half", 2.5f),
            chapter("two", 2f),
            chapter("five", 5f),
        )

        assertEquals(listOf(ChapterGap(2f, 2)), chapterGaps(chapters))
    }

    @Test
    fun dedupeDropsRepeatedUrlAndNumberPlusScanlator() {
        val chapters = listOf(
            chapter("a", 1f, scanlator = "Group"),
            chapter("a", 1f, scanlator = "Group"),
            chapter("b", 1f, scanlator = " group "),
            chapter("c", 1f, scanlator = "Other"),
            chapter("d", -1f),
            chapter("e", -1f),
        )

        assertEquals(listOf("a", "c", "d", "e"), dedupeChapters(chapters).map { it.url })
    }

    // ---------------------------------------------------------------- library

    @Test
    fun unreadCountUsesReadFlagsOnly() {
        val chapters = listOf(chapter("a", 1f), chapter("b", 2f), chapter("c", 3f))

        assertEquals(
            2,
            unreadCount(chapters, mapOf("a" to ChapterProgress(read = true))),
        )
    }

    @Test
    fun libraryFiltersCompose() {
        val entries = listOf(
            entry("unread", status = 0, source = "A", language = "en"),
            entry("started", status = 0, source = "A", language = "en"),
            entry("done", status = 2, source = "B", language = "ar"),
        )
        val unread = mapOf("1|unread" to 3, "1|started" to 0, "1|done" to 0)
        val started = setOf("1|started")

        assertEquals(
            listOf("unread"),
            filterLibrary(entries, MangaLibraryFilter(unreadOnly = true), started, unread)
                .map { it.url },
        )
        assertEquals(
            listOf("started"),
            filterLibrary(entries, MangaLibraryFilter(startedOnly = true), started, unread)
                .map { it.url },
        )
        assertEquals(
            listOf("done"),
            filterLibrary(
                entries,
                MangaLibraryFilter(completedOnly = true, sourceName = "B", language = "ar"),
                started,
                unread,
            ).map { it.url },
        )
        assertEquals(
            entries.map { it.url },
            filterLibrary(entries, MangaLibraryFilter(), started, unread).map { it.url },
        )
    }

    @Test
    fun librarySortsByTitleIgnoringCaseBothWays() {
        val entries = listOf(entry("b", "beta"), entry("a", "Alpha"), entry("c", "gamma"))

        assertEquals(
            listOf("Alpha", "beta", "gamma"),
            sortLibrary(
                entries,
                MangaLibrarySort.TITLE,
                descending = false,
                unread = emptyMap(),
                totals = emptyMap(),
                lastUpdated = emptyMap(),
            ).map { it.title },
        )
        assertEquals(
            listOf("gamma", "beta", "Alpha"),
            sortLibrary(
                entries,
                MangaLibrarySort.TITLE,
                descending = true,
                unread = emptyMap(),
                totals = emptyMap(),
                lastUpdated = emptyMap(),
            ).map { it.title },
        )
    }

    @Test
    fun librarySortsByUnreadAndNewestUpdate() {
        val entries = listOf(entry("a"), entry("b"))
        val unread = mapOf("1|a" to 1, "1|b" to 5)
        val updates = mapOf("1|a" to 100L)

        assertEquals(
            listOf("b", "a"),
            sortLibrary(
                entries,
                MangaLibrarySort.UNREAD,
                descending = true,
                unread = unread,
                totals = emptyMap(),
                lastUpdated = emptyMap(),
            ).map { it.url },
        )
        assertEquals(
            listOf("a", "b"),
            sortLibrary(
                entries,
                MangaLibrarySort.LAST_UPDATED,
                descending = true,
                unread = unread,
                totals = emptyMap(),
                lastUpdated = updates,
            ).map { it.url },
        )
    }

    // ---------------------------------------------------------------- history

    @Test
    fun historyGroupsByDayNewestFirst() {
        val zone = ZoneOffset.UTC
        val day = LocalDate.of(2026, 11, 3)
        fun at(date: LocalDate, hour: Int): Long =
            date.atStartOfDay().toInstant(zone).toEpochMilli() + hour * 3_600_000L

        val entries = listOf(
            MangaHistoryEntry(mangaUrl = "older", readAt = at(day, 8)),
            MangaHistoryEntry(mangaUrl = "newer", readAt = at(day, 10)),
            MangaHistoryEntry(mangaUrl = "yesterday", readAt = at(day.minusDays(1), 23)),
            MangaHistoryEntry(mangaUrl = "last-month", readAt = at(day.minusDays(33), 12)),
        )

        val groups = groupHistory(entries, zone, day)

        assertEquals(3, groups.size)
        assertEquals(
            at(day, 0),
            groups.first().dayStart,
        )
        assertEquals(
            listOf("newer", "older"),
            groups.first().entries.map { it.mangaUrl },
        )
        assertEquals(
            at(day.minusDays(1), 0),
            groups[1].dayStart,
        )
    }

    // ---------------------------------------------------------------- reader progress

    @Test
    fun pageIsClampedToTheChapter() {
        assertEquals(0, clampPage(-3, 10))
        assertEquals(0, clampPage(0, 10))
        assertEquals(9, clampPage(10, 10))
        assertEquals(4, clampPage(4, 10))
        assertEquals(0, clampPage(4, 0))
    }

    @Test
    fun progressWritesAreThrottled() {
        assertTrue(shouldPersistProgress(lastSavedAt = 1_000L, now = 4_000L))
        assertFalse(shouldPersistProgress(lastSavedAt = 1_000L, now = 3_999L))
        assertFalse(shouldPersistProgress(lastSavedAt = 0L, now = 0L))
    }

    @Test
    fun lastPageMarksTheChapterRead() {
        assertFalse(isLastPage(8, 10))
        assertTrue(isLastPage(9, 10))
        assertTrue(isLastPage(12, 10))
        assertFalse(isLastPage(0, 0))
    }

    // ---------------------------------------------------------------- tap zones

    @Test
    fun rightAndLeftSwapsWithReadingDirection() {
        assertEquals(TapAction.FORWARD, tapAction(0.1f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, readingRtl = true))
        assertEquals(TapAction.BACKWARD, tapAction(0.9f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, readingRtl = true))
        assertEquals(TapAction.BACKWARD, tapAction(0.1f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, readingRtl = false))
        assertEquals(TapAction.FORWARD, tapAction(0.9f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, readingRtl = false))
        assertEquals(TapAction.MENU, tapAction(0.5f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT))
    }

    @Test
    fun horizontalInvertMovesTheActionsWithTheRegions() {
        assertEquals(
            TapAction.BACKWARD,
            tapAction(0.1f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, TapInvert.HORIZONTAL),
        )
        assertEquals(
            TapAction.FORWARD,
            tapAction(0.9f, 0.5f, TapZoneLayout.RIGHT_AND_LEFT, TapInvert.HORIZONTAL),
        )
    }

    @Test
    fun lShapedAndEdgeLayoutsMapEveryZone() {
        assertEquals(TapAction.BACKWARD, tapAction(0.5f, 0.1f, TapZoneLayout.L_SHAPED))
        assertEquals(TapAction.FORWARD, tapAction(0.5f, 0.9f, TapZoneLayout.L_SHAPED))
        assertEquals(TapAction.BACKWARD, tapAction(0.1f, 0.5f, TapZoneLayout.L_SHAPED))
        assertEquals(TapAction.FORWARD, tapAction(0.9f, 0.5f, TapZoneLayout.L_SHAPED))
        assertEquals(TapAction.MENU, tapAction(0.5f, 0.5f, TapZoneLayout.L_SHAPED))

        assertEquals(TapAction.FORWARD, tapAction(0.1f, 0.5f, TapZoneLayout.EDGE))
        assertEquals(TapAction.BACKWARD, tapAction(0.5f, 0.9f, TapZoneLayout.EDGE))
    }

    @Test
    fun horizontalInvertMirrorsEveryLayoutForBothDirections() {
        for (layout in TapZoneLayout.entries.filter { it != TapZoneLayout.DISABLED }) {
            for (rtl in listOf(true, false)) {
                assertEquals(
                    tapAction(0.1f, 0.5f, layout, TapInvert.NONE, rtl),
                    tapAction(0.9f, 0.5f, layout, TapInvert.HORIZONTAL, rtl),
                )
                assertEquals(
                    tapAction(0.9f, 0.5f, layout, TapInvert.NONE, rtl),
                    tapAction(0.1f, 0.5f, layout, TapInvert.HORIZONTAL, rtl),
                )
            }
        }
    }

    @Test
    fun everyLayoutAndInvertCombinationIsHitTestable() {
        for (layout in TapZoneLayout.entries) {
            for (invert in TapInvert.entries) {
                for (rtl in listOf(true, false)) {
                    for (point in listOf(0.1f to 0.5f, 0.5f to 0.5f, 0.9f to 0.9f)) {
                        assertNotNull(tapAction(point.first, point.second, layout, invert, rtl))
                    }
                }
            }
        }
    }

    @Test
    fun disabledLayoutAlwaysOpensTheMenu() {
        assertEquals(TapAction.MENU, tapAction(0.1f, 0.1f, TapZoneLayout.DISABLED))
        assertTrue(tapRegions(TapZoneLayout.DISABLED).isEmpty())
    }

    // ---------------------------------------------------------------- categories

    @Test
    fun mangaCategoryMoveLeavesVideoMappingsAlone() {
        val mangaMappings = mutableMapOf("1|a" to "cat")
        val videoMappings = mutableMapOf("1|a" to "cat")

        val moved = FavoriteCategoryLogic.move(mangaMappings, listOf("1|a"), null)

        assertEquals(emptyList<String>(), itemsInCategory(listOf("1|a"), moved, setOf("cat"), "cat") { it })
        assertEquals(listOf("1|a"), itemsInCategory(listOf("1|a"), moved, setOf("cat"), null) { it })
        assertEquals(listOf("1|a"), itemsInCategory(listOf("1|a"), videoMappings, setOf("cat"), "cat") { it })
    }

    @Test
    fun mangaFoldersStayAwayFromTheVideoFolders() {
        assertEquals("manga_library", MangaLibraryStore.LIBRARY)
        assertEquals("manga_chapters", MangaChapterStore.CHAPTERS)
        assertEquals("manga_progress", MangaChapterStore.PROGRESS)
        assertEquals("manga_history", MangaHistoryStore.HISTORY)
        assertEquals("manga_categories", MangaCategoryStore.CATEGORIES)
        assertEquals("manga_category_of", MangaCategoryStore.MAPPINGS)
        assertEquals("manga_page_lists", MangaPageListStore.FOLDER)

        assertTrue(MangaCategoryStore.CATEGORIES != "favorite_categories")
        assertTrue(MangaCategoryStore.MAPPINGS != "favorite_category_of")
    }

    @Test
    fun videoStoresKeepTheirOldFolderNames() {
        fun constant(className: String, fieldName: String): Any? {
            val field = Class.forName(className).getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(null)
        }

        assertEquals(
            "favorites",
            constant("com.kodastream.app.data.LibraryStore", "FAVORITES"),
        )
        assertEquals(
            "watchlist",
            constant("com.kodastream.app.data.LibraryStore", "WATCHLIST"),
        )
        assertEquals(
            "watch_history",
            constant("com.kodastream.app.data.HistoryStore", "STORE_KEY"),
        )
        assertEquals(
            "favorite_categories",
            constant("com.kodastream.app.data.FavoriteCategoryStore", "CATEGORIES"),
        )
        assertEquals(
            "favorite_category_of",
            constant("com.kodastream.app.data.FavoriteCategoryStore", "MAPPINGS"),
        )
    }

    @Test
    fun deletingACategoryKeepsTheOtherMappingsAndReindexes() {
        val categories = listOf(
            FavoriteCategory(id = "cat", name = "Cat", order = 0, createdAt = 1L),
            FavoriteCategory(id = "other", name = "Other", order = 1, createdAt = 2L),
        )

        val remaining = FavoriteCategoryLogic.delete(categories, "cat")

        assertEquals(listOf("other"), remaining.map { it.id })
        assertEquals(0, remaining.single().order)
    }
}
