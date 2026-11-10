package com.kodastream.app.ui.manga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.manga.MangaCategoryStore
import com.kodastream.app.manga.MangaChapterStore
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.key
import com.kodastream.app.manga.mangaKey
import com.kodastream.app.manga.newestChapterDate
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.syncChapters
import com.kodastream.app.manga.unreadCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.withContext

/** Derived per-title numbers the library sorts and filters by. */
data class MangaLibraryStats(
    val unread: Map<String, Int> = emptyMap(),
    val totals: Map<String, Int> = emptyMap(),
    val lastUpdated: Map<String, Long> = emptyMap(),
    val started: Set<String> = emptySet(),
)

/** One chapter-update run over the visible library. */
data class MangaUpdateCheck(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val failures: List<String> = emptyList(),
    /** The automatic check on open stays silent unless something failed. */
    val automatic: Boolean = false,
)

/**
 * The manga library reads its own `manga_*` stores and checks sources two at
 * a time, so a slow extension cannot block the rest of the list.
 */
class MangaLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = MangaSourceManager.get(app)
    private var checkJob: Job? = null

    private val _entries = MutableStateFlow<List<MangaEntry>>(emptyList())
    val entries: StateFlow<List<MangaEntry>> = _entries.asStateFlow()

    private val _categories = MutableStateFlow<List<FavoriteCategory>>(emptyList())
    val categories: StateFlow<List<FavoriteCategory>> = _categories.asStateFlow()

    private val _categoryOf = MutableStateFlow<Map<String, String>>(emptyMap())
    val categoryOf: StateFlow<Map<String, String>> = _categoryOf.asStateFlow()

    private val _stats = MutableStateFlow(MangaLibraryStats())
    val stats: StateFlow<MangaLibraryStats> = _stats.asStateFlow()

    private val _check = MutableStateFlow(MangaUpdateCheck())
    val check: StateFlow<MangaUpdateCheck> = _check.asStateFlow()

    init {
        // Orphaned mappings are ignored everywhere; this just drops them.
        MangaCategoryStore.prune(app)
        viewModelScope.launch {
            combine(MangaLibraryStore.revision, MangaChapterStore.revision) { _, _ -> }
                .collect { reload() }
        }
        maybeAutoCheck()
    }

    private fun reload() {
        val entries = MangaLibraryStore.entries(app)
        val progress = MangaChapterStore.allProgress(app)
            .groupBy { mangaKey(it.sourceId, it.mangaUrl) }
        val unread = HashMap<String, Int>(entries.size)
        val totals = HashMap<String, Int>(entries.size)
        val lastUpdated = HashMap<String, Long>(entries.size)
        val started = HashSet<String>()
        entries.forEach { entry ->
            val key = entry.key()
            val chapters = MangaChapterStore.chapters(app, entry.sourceId, entry.url)
            val state = progress[key].orEmpty().associate { it.chapterUrl to it.progress }
            totals[key] = chapters.size
            lastUpdated[key] = newestChapterDate(chapters)
            unread[key] = unreadCount(chapters, state)
            if (state.values.any { it.read || it.lastPageRead > 0 }) started.add(key)
        }
        _entries.value = entries
        _categories.value = MangaCategoryStore.categories(app)
        _categoryOf.value = MangaCategoryStore.mappings(app)
        _stats.value = MangaLibraryStats(
            unread = unread,
            totals = totals,
            lastUpdated = lastUpdated,
            started = started,
        )
    }

    /**
     * The automatic check runs only when Settings allows it and the last one
     * is older than 12 hours. It waits for extensions when they are still
     * loading, so a cold start does not check against an empty source list.
     */
    private fun maybeAutoCheck() {
        if (!MangaPrefs.checkUpdatesOnOpen(app)) return
        val last = MangaPrefs.libraryLastCheck(app)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        viewModelScope.launch {
            val sources = manager.sources.first { it.isNotEmpty() }
            if (sources.isEmpty()) return@launch
            val entries = MangaLibraryStore.entries(app)
            if (entries.isNotEmpty()) startCheck(entries, automatic = true)
        }
    }

    /** Runs at most one check; the visible category is what gets checked. */
    fun checkUpdates(entries: List<MangaEntry>) {
        startCheck(entries, automatic = false)
    }

    private fun startCheck(entries: List<MangaEntry>, automatic: Boolean) {
        if (_check.value.running || entries.isEmpty()) return
        checkJob = viewModelScope.launch {
            _check.value = MangaUpdateCheck(
                running = true,
                total = entries.size,
                automatic = automatic,
            )
            var completed = false
            try {
                val done = AtomicInteger()
                coroutineScope {
                    val gate = Semaphore(2)
                    entries.map { entry ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                val failure = checkOne(entry)
                                val finished = done.incrementAndGet()
                                _check.update {
                                    it.copy(
                                        done = finished,
                                        failures = if (failure == null) it.failures
                                        else it.failures + failure,
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
                completed = true
            } finally {
                if (completed) MangaPrefs.setLibraryLastCheck(app, System.currentTimeMillis())
                _check.update { it.copy(running = false) }
            }
        }
    }

    fun cancelCheck() {
        checkJob?.cancel()
    }

    private suspend fun checkOne(entry: MangaEntry): String? = try {
        val source = manager.catalogueSourceById(entry.sourceId)
            ?: return "${entry.title}: ${app.getString(R.string.manga_library_source_gone)}"
        val cached = MangaChapterStore.chapters(app, entry.sourceId, entry.url)
        val update = withContext(Dispatchers.IO) {
            source.getMangaUpdate(
                manga = entry.toSManga(),
                chapters = cached.map { it.toSChapter() },
                fetchDetails = false,
                fetchChapters = true,
            )
        }
        val fetched = update.chapters.mapIndexedNotNull { index, chapter ->
            chapter.toModel(index)
        }
        val sync = syncChapters(cached, fetched)
        MangaChapterStore.saveChapters(app, entry.sourceId, entry.url, sync.chapters)
        MangaChapterStore.applyAliases(app, entry.sourceId, entry.url, sync.aliases)
        MangaLibraryStore.setLastChecked(app, entry.sourceId, entry.url, System.currentTimeMillis())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        "${entry.title}: ${rootMessage(e)}"
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
