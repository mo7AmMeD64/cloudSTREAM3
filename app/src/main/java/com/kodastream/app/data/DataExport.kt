package com.kodastream.app.data

import android.content.Context
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import java.time.Instant

const val DATA_EXPORT_VERSION = 1

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataExport(
    @JsonProperty("version") val version: Int = DATA_EXPORT_VERSION,
    @JsonProperty("exported_at") val exportedAt: String = Instant.now().toString(),
    @JsonProperty("library") val library: LibraryExport = LibraryExport(),
    @JsonProperty("history") val history: HistoryExport = HistoryExport(),
    @JsonProperty("watched") val watched: WatchedExport = WatchedExport(),
    @JsonProperty("search_history") val searchHistory: List<String> = emptyList(),
    @JsonProperty("app_prefs") val appPrefs: Map<String, Any?> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LibraryExport(
    @JsonProperty("favorites") val favorites: List<LibraryStore.LibraryItem> = emptyList(),
    @JsonProperty("watchlist") val watchlist: List<LibraryStore.LibraryItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HistoryExport(
    @JsonProperty("entries") val entries: List<HistoryStore.HistoryEntry> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WatchedExport(
    @JsonProperty("entries") val entries: List<WatchedStore.WatchedEntry> = emptyList(),
)

enum class ImportMode {
    MERGE,
    REPLACE,
}

data class ImportSectionSummary(
    val incomingCount: Int,
    val overwriteCount: Int,
)

data class ImportSummary(
    val favorites: ImportSectionSummary,
    val watchlist: ImportSectionSummary,
    val history: ImportSectionSummary,
    val watched: ImportSectionSummary,
    val searchHistory: ImportSectionSummary,
    val appPrefs: ImportSectionSummary,
) {
    val totalIncoming: Int
        get() = favorites.incomingCount + watchlist.incomingCount + history.incomingCount +
            watched.incomingCount + searchHistory.incomingCount + appPrefs.incomingCount

    val totalOverwritten: Int
        get() = favorites.overwriteCount + watchlist.overwriteCount + history.overwriteCount +
            watched.overwriteCount + searchHistory.overwriteCount + appPrefs.overwriteCount
}

/** Pure merge logic and validation for DataExport documents. */
object DataExportLogic {
    fun validateVersion(version: Int) {
        if (version != DATA_EXPORT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version (expected $DATA_EXPORT_VERSION)")
        }
    }

    fun parseAndValidate(json: String): DataExport {
        val data = mapper.readValue<DataExport>(json)
        validateVersion(data.version)
        return data
    }

    fun toJson(data: DataExport): String {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
    }

    fun mergeLibrary(target: LibraryExport, source: LibraryExport): LibraryExport {
        fun mergeItems(
            targetItems: List<LibraryStore.LibraryItem>,
            sourceItems: List<LibraryStore.LibraryItem>,
        ): List<LibraryStore.LibraryItem> {
            val map = LinkedHashMap<String, LibraryStore.LibraryItem>()
            for (item in targetItems) {
                map["${item.apiName}|${item.url}"] = item
            }
            for (sourceItem in sourceItems) {
                val key = "${sourceItem.apiName}|${sourceItem.url}"
                val existing = map[key]
                if (existing == null || sourceItem.addedAt >= existing.addedAt) {
                    map[key] = sourceItem
                }
            }
            return map.values.sortedByDescending { it.addedAt }
        }

        return LibraryExport(
            favorites = mergeItems(target.favorites, source.favorites),
            watchlist = mergeItems(target.watchlist, source.watchlist),
        )
    }

    fun mergeHistory(
        target: HistoryExport,
        source: HistoryExport,
        maxEntries: Int = 200,
    ): HistoryExport {
        val map = LinkedHashMap<String, HistoryStore.HistoryEntry>()
        for (entry in target.entries) {
            map[entry.key] = entry
        }
        for (sourceEntry in source.entries) {
            val existing = map[sourceEntry.key]
            if (existing == null || sourceEntry.lastWatchedAt >= existing.lastWatchedAt) {
                map[sourceEntry.key] = sourceEntry
            }
        }
        val merged = map.values.sortedByDescending { it.lastWatchedAt }.take(maxEntries)
        return HistoryExport(entries = merged)
    }

    fun mergeWatched(target: WatchedExport, source: WatchedExport): WatchedExport {
        val map = LinkedHashMap<String, WatchedStore.WatchedEntry>()
        for (entry in target.entries) {
            map[entry.key] = entry
        }
        for (sourceEntry in source.entries) {
            val existing = map[sourceEntry.key]
            if (existing == null || sourceEntry.watchedAt >= existing.watchedAt) {
                map[sourceEntry.key] = sourceEntry
            }
        }
        return WatchedExport(entries = map.values.sortedByDescending { it.watchedAt })
    }

    fun mergeSearchHistory(
        target: List<String>,
        source: List<String>,
        max: Int = 20,
    ): List<String> {
        val combined = ArrayList<String>(source.size + target.size)
        val seen = HashSet<String>()
        // Source queries first (newest imported queries take priority)
        for (q in source) {
            val trimmed = q.trim()
            if (trimmed.length >= 2 && seen.add(trimmed.lowercase())) {
                combined.add(trimmed)
            }
        }
        // Then existing queries
        for (q in target) {
            val trimmed = q.trim()
            if (trimmed.length >= 2 && seen.add(trimmed.lowercase())) {
                combined.add(trimmed)
            }
        }
        return combined.take(max)
    }

    fun mergePrefs(target: Map<String, Any?>, source: Map<String, Any?>): Map<String, Any?> {
        val filteredTarget = target.filter { (k, _) -> AppPrefs.isExportablePrefKey(k) }
        val filteredSource = source.filter { (k, _) -> AppPrefs.isExportablePrefKey(k) }
        return filteredTarget + filteredSource
    }

    fun merge(target: DataExport, source: DataExport): DataExport {
        validateVersion(source.version)
        return DataExport(
            version = DATA_EXPORT_VERSION,
            exportedAt = Instant.now().toString(),
            library = mergeLibrary(target.library, source.library),
            history = mergeHistory(target.history, source.history),
            watched = mergeWatched(target.watched, source.watched),
            searchHistory = mergeSearchHistory(target.searchHistory, source.searchHistory),
            appPrefs = mergePrefs(target.appPrefs, source.appPrefs),
        )
    }

    fun computeSummary(target: DataExport, source: DataExport): ImportSummary {
        fun computeSection(targetKeys: Set<String>, sourceKeys: List<String>): ImportSectionSummary {
            var overwrite = 0
            for (k in sourceKeys) {
                if (k in targetKeys) overwrite++
            }
            return ImportSectionSummary(incomingCount = sourceKeys.size, overwriteCount = overwrite)
        }

        val targetFavKeys = target.library.favorites.map { "${it.apiName}|${it.url}" }.toSet()
        val sourceFavKeys = source.library.favorites.map { "${it.apiName}|${it.url}" }
        val favSummary = computeSection(targetFavKeys, sourceFavKeys)

        val targetWatchlistKeys = target.library.watchlist.map { "${it.apiName}|${it.url}" }.toSet()
        val sourceWatchlistKeys = source.library.watchlist.map { "${it.apiName}|${it.url}" }
        val watchlistSummary = computeSection(targetWatchlistKeys, sourceWatchlistKeys)

        val targetHistoryKeys = target.history.entries.map { it.key }.toSet()
        val sourceHistoryKeys = source.history.entries.map { it.key }
        val historySummary = computeSection(targetHistoryKeys, sourceHistoryKeys)

        val targetWatchedKeys = target.watched.entries.map { it.key }.toSet()
        val sourceWatchedKeys = source.watched.entries.map { it.key }
        val watchedSummary = computeSection(targetWatchedKeys, sourceWatchedKeys)

        val targetSearchKeys = target.searchHistory.map { it.trim().lowercase() }.toSet()
        val sourceSearchKeys = source.searchHistory.map { it.trim().lowercase() }
        val searchSummary = computeSection(targetSearchKeys, sourceSearchKeys)

        val targetPrefKeys = target.appPrefs.filter { (k, _) -> AppPrefs.isExportablePrefKey(k) }.keys
        val sourcePrefKeys = source.appPrefs.filter { (k, _) -> AppPrefs.isExportablePrefKey(k) }.keys.toList()
        val prefSummary = computeSection(targetPrefKeys, sourcePrefKeys)

        return ImportSummary(
            favorites = favSummary,
            watchlist = watchlistSummary,
            history = historySummary,
            watched = watchedSummary,
            searchHistory = searchSummary,
            appPrefs = prefSummary,
        )
    }
}

/** Android Context and SAF bridge for exporting and importing data. */
object DataExportManager {
    fun createExport(context: Context): DataExport {
        return DataExport(
            version = DATA_EXPORT_VERSION,
            exportedAt = Instant.now().toString(),
            library = LibraryExport(
                favorites = LibraryStore.favorites(context),
                watchlist = LibraryStore.watchlist(context),
            ),
            history = HistoryExport(
                entries = HistoryStore.current(context),
            ),
            watched = WatchedExport(
                entries = WatchedStore.getEntries(context),
            ),
            searchHistory = SearchHistoryStore.current(context),
            appPrefs = AppPrefs.getExportablePrefs(context),
        )
    }

    fun exportToUri(context: Context, uri: Uri): Result<Unit> = runCatching {
        val export = createExport(context)
        val json = DataExportLogic.toJson(export)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    fun readFromUri(context: Context, uri: Uri): Result<DataExport> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")
        DataExportLogic.parseAndValidate(json)
    }

    fun applyImport(context: Context, mode: ImportMode, imported: DataExport) {
        val dataToApply = when (mode) {
            ImportMode.MERGE -> {
                val current = createExport(context)
                DataExportLogic.merge(current, imported)
            }
            ImportMode.REPLACE -> imported
        }

        LibraryStore.restore(
            context = context,
            favorites = dataToApply.library.favorites,
            watchlist = dataToApply.library.watchlist,
        )
        HistoryStore.restore(context, dataToApply.history.entries)
        WatchedStore.restore(context, dataToApply.watched.entries)
        SearchHistoryStore.restore(context, dataToApply.searchHistory)
        AppPrefs.restorePrefs(
            context = context,
            newPrefs = dataToApply.appPrefs,
            replace = (mode == ImportMode.REPLACE),
        )
    }
}
