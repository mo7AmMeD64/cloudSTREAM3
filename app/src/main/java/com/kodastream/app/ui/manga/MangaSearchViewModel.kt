package com.kodastream.app.ui.manga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaSearchHistoryStore
import com.kodastream.app.manga.extension.LoadedSource
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.selectSource
import com.kodastream.app.manga.urlOrNull
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One source's shelf in the global search. */
data class MangaSearchShelf(
    val sourceId: Long,
    val sourceName: String,
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<SManga> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

/**
 * Global search over the pinned sources, or the selected one when nothing is
 * pinned. At most four sources run at once, and a new query cancels the old
 * one so no shelf can arrive from a search the user abandoned.
 */
class MangaSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = MangaSourceManager.get(app)

    val sources: StateFlow<List<LoadedSource>> = manager.sources
    val history: StateFlow<List<String>?> = MangaSearchHistoryStore.entries

    private val _shelves = MutableStateFlow<List<MangaSearchShelf>>(emptyList())
    val shelves: StateFlow<List<MangaSearchShelf>> = _shelves.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched.asStateFlow()

    private var job: Job? = null

    init {
        MangaSearchHistoryStore.current(app)
    }

    /** Sources the 18+ gate allows into a global search. */
    fun visibleSources(): List<LoadedSource> {
        val showNsfw = MangaPrefs.showNsfwGlobalSearch(app)
        return sources.value.filter { showNsfw || !it.isNsfw() }
    }

    /** The sources a plain search hits: all pinned favourites, else the selection. */
    fun defaultTargets(): List<LoadedSource> {
        val visible = visibleSources()
        val pinned = runCatching { MangaPrefs.pinnedSources(app) }.getOrDefault(emptySet())
        val pinnedSources = visible.filter { it.source.id in pinned }
        if (pinnedSources.isNotEmpty()) return pinnedSources
        val selected = selectSource(
            visible.map { com.kodastream.app.manga.SourceChoice(it.source.id, it.source.name, it.language) },
            MangaPrefs.selectedSourceId(app),
            showNsfw = true,
        )
        return visible.filter { it.source.id == selected?.id }
    }

    fun submit(query: String, allSources: Boolean = false) {
        val text = query.trim()
        if (text.isEmpty()) return
        MangaSearchHistoryStore.add(app, text)
        val targets = if (allSources) visibleSources() else defaultTargets()
        job?.cancel()
        _searched.value = true
        _searching.value = targets.isNotEmpty()
        _shelves.value = targets.map {
            MangaSearchShelf(it.source.id, it.source.name)
        }
        job = viewModelScope.launch {
            val gate = Semaphore(4)
            coroutineScope {
                targets.forEach { entry ->
                    launch {
                        gate.withPermit {
                            val catalogue = entry.source as? CatalogueSource
                            if (catalogue == null) {
                                update(entry.source.id) {
                                    it.copy(loading = false, error = "")
                                }
                                return@withPermit
                            }
                            try {
                                val page = withContext(Dispatchers.IO) {
                                    catalogue.getSearchManga(1, text, FilterList())
                                }
                                update(entry.source.id) {
                                    it.copy(
                                        loading = false,
                                        items = page.mangas
                                            .filter { manga -> !manga.urlOrNull().isNullOrBlank() }
                                            .distinctBy { manga -> manga.urlOrNull() },
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                update(entry.source.id) {
                                    it.copy(loading = false, error = rootMessage(e))
                                }
                            }
                        }
                    }
                }
            }
            _searching.value = false
        }
    }

    private fun update(sourceId: Long, block: (MangaSearchShelf) -> MangaSearchShelf) {
        _shelves.value = _shelves.value.map { if (it.sourceId == sourceId) block(it) else it }
    }

    /** Screen left mid-search: stop the requests but keep what already arrived. */
    fun cancel() {
        job?.cancel()
        _searching.value = false
    }

    fun clear() {
        job?.cancel()
        _shelves.value = emptyList()
        _searching.value = false
        _searched.value = false
    }

    fun removeHistory(entry: String) = MangaSearchHistoryStore.remove(app, entry)

    fun clearHistory() = MangaSearchHistoryStore.clear(app)
}
