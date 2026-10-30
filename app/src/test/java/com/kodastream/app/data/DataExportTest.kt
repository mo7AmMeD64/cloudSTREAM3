package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DataExportTest {

    private fun sampleLibraryItem(
        apiName: String = "TestProvider",
        url: String = "https://example.com/item/1",
        name: String = "Test Movie",
        addedAt: Long = 1000L,
    ) = LibraryStore.LibraryItem(
        apiName = apiName,
        url = url,
        name = name,
        addedAt = addedAt,
    )

    private fun sampleHistoryEntry(
        key: String = "TestProvider|https://example.com/item/1",
        title: String = "Test Movie",
        lastWatchedAt: Long = 1000L,
        positionMs: Long = 5000L,
    ) = HistoryStore.HistoryEntry(
        key = key,
        apiName = "TestProvider",
        url = "https://example.com/item/1",
        title = title,
        data = "data",
        resumeKey = key,
        positionMs = positionMs,
        durationMs = 60000L,
        lastWatchedAt = lastWatchedAt,
    )

    private fun sampleWatchedEntry(
        key: String = "TestProvider|https://example.com/series/1|1|1",
        watchedAt: Long = 1000L,
    ) = WatchedStore.WatchedEntry(
        key = key,
        watchedAt = watchedAt,
    )

    @Test
    fun emptyTarget() {
        val target = DataExport()
        val source = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(sampleLibraryItem(name = "Source Fav")),
                watchlist = listOf(sampleLibraryItem(name = "Source Watchlist")),
            ),
            history = HistoryExport(
                entries = listOf(sampleHistoryEntry(title = "Source History")),
            ),
            watched = WatchedExport(
                entries = listOf(sampleWatchedEntry()),
            ),
            searchHistory = listOf("Source Query"),
            appPrefs = mapOf("keep_screen_on" to true, "ui_style" to "glass"),
        )

        val merged = DataExportLogic.merge(target, source)

        assertEquals(1, merged.library.favorites.size)
        assertEquals("Source Fav", merged.library.favorites.first().name)
        assertEquals(1, merged.library.watchlist.size)
        assertEquals("Source Watchlist", merged.library.watchlist.first().name)
        assertEquals(1, merged.history.entries.size)
        assertEquals("Source History", merged.history.entries.first().title)
        assertEquals(1, merged.watched.entries.size)
        assertEquals(listOf("Source Query"), merged.searchHistory)
        assertEquals(true, merged.appPrefs["keep_screen_on"])
        assertEquals("glass", merged.appPrefs["ui_style"])
    }

    @Test
    fun emptySource() {
        val target = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(sampleLibraryItem(name = "Target Fav")),
                watchlist = listOf(sampleLibraryItem(name = "Target Watchlist")),
            ),
            history = HistoryExport(
                entries = listOf(sampleHistoryEntry(title = "Target History")),
            ),
            watched = WatchedExport(
                entries = listOf(sampleWatchedEntry()),
            ),
            searchHistory = listOf("Target Query"),
            appPrefs = mapOf("keep_screen_on" to false, "ui_style" to "material"),
        )
        val source = DataExport(version = 1)

        val merged = DataExportLogic.merge(target, source)

        assertEquals(1, merged.library.favorites.size)
        assertEquals("Target Fav", merged.library.favorites.first().name)
        assertEquals(1, merged.library.watchlist.size)
        assertEquals("Target Watchlist", merged.library.watchlist.first().name)
        assertEquals(1, merged.history.entries.size)
        assertEquals("Target History", merged.history.entries.first().title)
        assertEquals(1, merged.watched.entries.size)
        assertEquals(listOf("Target Query"), merged.searchHistory)
        assertEquals(false, merged.appPrefs["keep_screen_on"])
        assertEquals("material", merged.appPrefs["ui_style"])
    }

    @Test
    fun overlappingKeysKeepNewest() {
        // Library: overlapping items
        val itemOld = sampleLibraryItem(url = "https://example.com/same", name = "Old Name", addedAt = 100L)
        val itemNew = sampleLibraryItem(url = "https://example.com/same", name = "Newer Name", addedAt = 200L)
        val itemTargetOnly = sampleLibraryItem(url = "https://example.com/target", name = "Target Only", addedAt = 50L)
        val itemSourceOnly = sampleLibraryItem(url = "https://example.com/source", name = "Source Only", addedAt = 80L)

        // History: overlapping entries
        val histOld = sampleHistoryEntry(key = "same|key", title = "Old Title", lastWatchedAt = 1000L, positionMs = 100L)
        val histNew = sampleHistoryEntry(key = "same|key", title = "Newer Title", lastWatchedAt = 2000L, positionMs = 999L)

        // Watched: overlapping entries
        val watchedOld = sampleWatchedEntry(key = "same|key|1|1", watchedAt = 500L)
        val watchedNew = sampleWatchedEntry(key = "same|key|1|1", watchedAt = 1500L)

        val target = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(itemOld, itemTargetOnly),
                watchlist = listOf(itemNew), // in target, this is the newer one
            ),
            history = HistoryExport(entries = listOf(histOld)),
            watched = WatchedExport(entries = listOf(watchedOld)),
            searchHistory = listOf("dune", "matrix"),
            appPrefs = mapOf("keep_screen_on" to false, "ui_style" to "material"),
        )

        val source = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(itemNew, itemSourceOnly), // in source, itemNew should overwrite itemOld
                watchlist = listOf(itemOld), // in source, itemOld is older, so itemNew from target should remain
            ),
            history = HistoryExport(entries = listOf(histNew)),
            watched = WatchedExport(entries = listOf(watchedNew)),
            searchHistory = listOf("Dune", "Blade Runner"), // "Dune" is duplicate, case-insensitive
            appPrefs = mapOf("keep_screen_on" to true, "playback_speed" to 1.5),
        )

        val merged = DataExportLogic.merge(target, source)

        // Favorites: should keep itemNew (addedAt 200) over itemOld (addedAt 100)
        assertEquals(3, merged.library.favorites.size)
        val favByKey = merged.library.favorites.associateBy { it.url }
        assertEquals("Newer Name", favByKey["https://example.com/same"]?.name)
        assertEquals(200L, favByKey["https://example.com/same"]?.addedAt)
        assertTrue(favByKey.containsKey("https://example.com/target"))
        assertTrue(favByKey.containsKey("https://example.com/source"))

        // Watchlist: should keep itemNew (addedAt 200) over itemOld (addedAt 100)
        assertEquals(1, merged.library.watchlist.size)
        assertEquals("Newer Name", merged.library.watchlist.first().name)
        assertEquals(200L, merged.library.watchlist.first().addedAt)

        // History: should keep histNew
        assertEquals(1, merged.history.entries.size)
        assertEquals("Newer Title", merged.history.entries.first().title)
        assertEquals(999L, merged.history.entries.first().positionMs)
        assertEquals(2000L, merged.history.entries.first().lastWatchedAt)

        // Watched: should keep watchedNew
        assertEquals(1, merged.watched.entries.size)
        assertEquals(1500L, merged.watched.entries.first().watchedAt)

        // Search history: case-insensitive deduplication, source queries first
        assertEquals(listOf("Dune", "Blade Runner", "matrix"), merged.searchHistory)

        // App prefs: source overwrites target keys, non-overlapping target keys kept
        assertEquals(true, merged.appPrefs["keep_screen_on"])
        assertEquals("material", merged.appPrefs["ui_style"])
        assertEquals(1.5, merged.appPrefs["playback_speed"])
    }

    @Test
    fun versionMismatchThrowsError() {
        // Higher version
        try {
            DataExportLogic.validateVersion(2)
            fail("Expected IllegalArgumentException for version 2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported backup version") == true)
        }

        // Version 0
        try {
            DataExportLogic.validateVersion(0)
            fail("Expected IllegalArgumentException for version 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported backup version") == true)
        }

        // Merge with mismatch version in source throws
        val target = DataExport(version = 1)
        val invalidSource = DataExport(version = 2)
        try {
            DataExportLogic.merge(target, invalidSource)
            fail("Expected IllegalArgumentException when merging source with version 2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported backup version") == true)
        }

        // Parsing JSON with version mismatch throws
        val jsonVersion99 = """{ "version": 99, "exported_at": "2026-10-30T00:00:00Z" }"""
        try {
            DataExportLogic.parseAndValidate(jsonVersion99)
            fail("Expected IllegalArgumentException when parsing JSON with version 99")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported backup version") == true)
        }
    }

    @Test
    fun appPrefsSkipsDownloadAndProviderKeys() {
        assertFalse(AppPrefs.isExportablePrefKey("selected_provider"))
        assertFalse(AppPrefs.isExportablePrefKey("download_tree_uri"))
        assertFalse(AppPrefs.isExportablePrefKey("download_wifi_only"))
        assertFalse(AppPrefs.isExportablePrefKey("download_max_concurrent"))
        assertFalse(AppPrefs.isExportablePrefKey("download_last_server"))
        assertFalse(AppPrefs.isExportablePrefKey("download_folders_migrated"))

        assertTrue(AppPrefs.isExportablePrefKey("keep_screen_on"))
        assertTrue(AppPrefs.isExportablePrefKey("ui_style"))
        assertTrue(AppPrefs.isExportablePrefKey("nav_position"))
        assertTrue(AppPrefs.isExportablePrefKey("playback_speed"))
        assertTrue(AppPrefs.isExportablePrefKey("pause_history"))
        assertTrue(AppPrefs.isExportablePrefKey("allow_invalid_ssl"))

        val targetPrefs = mapOf(
            "keep_screen_on" to false,
            "selected_provider" to "TargetProvider",
            "download_wifi_only" to false,
        )
        val sourcePrefs = mapOf(
            "keep_screen_on" to true,
            "selected_provider" to "SourceProvider",
            "download_wifi_only" to true,
            "ui_style" to "glass",
        )

        val merged = DataExportLogic.mergePrefs(targetPrefs, sourcePrefs)

        assertFalse(merged.containsKey("selected_provider"))
        assertFalse(merged.containsKey("download_wifi_only"))
        assertEquals(true, merged["keep_screen_on"])
        assertEquals("glass", merged["ui_style"])
    }

    @Test
    fun roundTripSerialization() {
        val original = DataExport(
            version = 1,
            exportedAt = "2026-10-30T08:00:00Z",
            library = LibraryExport(
                favorites = listOf(sampleLibraryItem(name = "Roundtrip Fav")),
                watchlist = listOf(sampleLibraryItem(name = "Roundtrip Watchlist")),
            ),
            history = HistoryExport(
                entries = listOf(sampleHistoryEntry(title = "Roundtrip Movie")),
            ),
            watched = WatchedExport(
                entries = listOf(sampleWatchedEntry()),
            ),
            searchHistory = listOf("sci-fi", "anime"),
            appPrefs = mapOf("keep_screen_on" to true, "ui_style" to "glass"),
        )

        val json = DataExportLogic.toJson(original)
        val parsed = DataExportLogic.parseAndValidate(json)

        assertEquals(original.version, parsed.version)
        assertEquals(original.exportedAt, parsed.exportedAt)
        assertEquals(1, parsed.library.favorites.size)
        assertEquals("Roundtrip Fav", parsed.library.favorites.first().name)
        assertEquals(1, parsed.library.watchlist.size)
        assertEquals("Roundtrip Watchlist", parsed.library.watchlist.first().name)
        assertEquals(1, parsed.history.entries.size)
        assertEquals("Roundtrip Movie", parsed.history.entries.first().title)
        assertEquals(1, parsed.watched.entries.size)
        assertEquals(listOf("sci-fi", "anime"), parsed.searchHistory)
        assertEquals(true, parsed.appPrefs["keep_screen_on"])
        assertEquals("glass", parsed.appPrefs["ui_style"])
    }

    @Test
    fun computeSummaryCounts() {
        val target = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(sampleLibraryItem(url = "url1")),
                watchlist = listOf(sampleLibraryItem(url = "url2")),
            ),
            history = HistoryExport(
                entries = listOf(sampleHistoryEntry(key = "key1")),
            ),
            watched = WatchedExport(
                entries = listOf(sampleWatchedEntry(key = "watched1")),
            ),
            searchHistory = listOf("query1"),
            appPrefs = mapOf("keep_screen_on" to true),
        )

        val source = DataExport(
            version = 1,
            library = LibraryExport(
                favorites = listOf(sampleLibraryItem(url = "url1"), sampleLibraryItem(url = "url_new")),
                watchlist = emptyList(),
            ),
            history = HistoryExport(
                entries = listOf(sampleHistoryEntry(key = "key1")),
            ),
            watched = WatchedExport(
                entries = listOf(sampleWatchedEntry(key = "watched1"), sampleWatchedEntry(key = "watched2")),
            ),
            searchHistory = listOf("query1", "query2"),
            appPrefs = mapOf("keep_screen_on" to false, "ui_style" to "glass"),
        )

        val summary = DataExportLogic.computeSummary(target, source)

        // Favorites: 2 incoming, 1 overwrite (url1)
        assertEquals(2, summary.favorites.incomingCount)
        assertEquals(1, summary.favorites.overwriteCount)

        // Watchlist: 0 incoming, 0 overwrite
        assertEquals(0, summary.watchlist.incomingCount)
        assertEquals(0, summary.watchlist.overwriteCount)

        // History: 1 incoming, 1 overwrite (key1)
        assertEquals(1, summary.history.incomingCount)
        assertEquals(1, summary.history.overwriteCount)

        // Watched: 2 incoming, 1 overwrite (watched1)
        assertEquals(2, summary.watched.incomingCount)
        assertEquals(1, summary.watched.overwriteCount)

        // Search: 2 incoming, 1 overwrite (query1)
        assertEquals(2, summary.searchHistory.incomingCount)
        assertEquals(1, summary.searchHistory.overwriteCount)

        // Prefs: 2 incoming, 1 overwrite (keep_screen_on)
        assertEquals(2, summary.appPrefs.incomingCount)
        assertEquals(1, summary.appPrefs.overwriteCount)

        assertEquals(9, summary.totalIncoming)
        assertEquals(5, summary.totalOverwritten)
    }
}
