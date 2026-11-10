package com.kodastream.app.ui.manga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.manga.FilterSnapshot
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.activeFilterCount
import com.kodastream.app.manga.extension.LoadedSource
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.mergeUnique
import com.kodastream.app.manga.restoreFilters
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.selectSource
import com.kodastream.app.manga.urlOrNull
import com.kodastream.app.manga.store.ContentWarning
import com.kodastream.app.manga.SourceChoice
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the browse grid needs, in one immutable snapshot. */
data class MangaBrowseState(
    val items: List<SManga> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val initialLoading: Boolean get() = loading && items.isEmpty()
    val loadingMore: Boolean get() = loading && items.isNotEmpty()
}

/**
 * One owner of the browse selection and its paged list. The selected source is
 * resolved from the saved id with [selectSource], never written back, so a
 * reload of extensions cannot fight the user's choice.
 */
class MangaBrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val manager = MangaSourceManager.get(app)

    private val savedId = MutableStateFlow(MangaPrefs.selectedSourceId(app))

    val sources: StateFlow<List<LoadedSource>> = manager.sources
    val loadingSources: StateFlow<Boolean> = manager.loading

    val selected: StateFlow<LoadedSource?> = combine(sources, savedId) { list, saved ->
        val choice = selectSource(list.map { it.toChoice() }, saved)
        list.firstOrNull { it.source.id == choice?.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tab = MutableStateFlow(MangaPrefs.browseTab(app))
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow(MangaBrowseState())
    val state: StateFlow<MangaBrowseState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var queryJob: Job? = null

    /** The source's own filter objects, holding the applied state. */
    private var filters: FilterList? = null
    private var filtersSourceId: Long = -1L

    private val _activeFilterCount = MutableStateFlow(0)
    val activeFilterCount: StateFlow<Int> = _activeFilterCount.asStateFlow()

    init {
        viewModelScope.launch {
            selected
                .map { it?.source?.id }
                .distinctUntilChanged()
                .collect {
                    filters = null
                    filtersSourceId = -1L
                    _activeFilterCount.value = 0
                    reload()
                }
        }
    }

    fun selectSource(id: Long) {
        MangaPrefs.setSelectedSourceId(app, id)
        savedId.value = id
    }

    fun setTab(index: Int) {
        if (_tab.value == index) return
        _tab.value = index
        MangaPrefs.setBrowseTab(app, index)
        _query.value = ""
        reload()
    }

    /** 400ms debounce, and the previous request is cancelled by [reload]. */
    fun setQuery(text: String) {
        _query.value = text
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(400)
            reload()
        }
    }

    fun clearQuery() {
        queryJob?.cancel()
        if (_query.value.isEmpty()) return
        _query.value = ""
        reload()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        fetch(page = 1, append = false)
    }

    fun retry() {
        if (_state.value.items.isEmpty()) reload() else loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.refreshing || current.endReached || current.items.isEmpty()) return
        fetch(page = current.page + 1, append = true)
    }

    fun reload() {
        loadJob?.cancel()
        _state.update {
            it.copy(items = emptyList(), page = 0, endReached = false, error = null)
        }
        fetch(page = 1, append = false)
    }

    private fun fetch(page: Int, append: Boolean) {
        val entry = selected.value ?: return
        val source = entry.source as? CatalogueSource ?: return
        val text = _query.value.trim()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    when {
                        text.isNotEmpty() -> source.getSearchManga(page, text, filtersFor(source))
                        _tab.value == 0 -> source.getPopularManga(page)
                        else -> source.getLatestUpdates(page)
                    }
                }
                _state.update { current ->
                    val incoming = result.mangas.filter {
                        !it.urlOrNull().isNullOrBlank()
                    }
                    current.copy(
                        items = if (append) {
                            mergeUnique(current.items, incoming) { it.urlOrNull().orEmpty() }
                        } else {
                            incoming.distinctBy { it.urlOrNull() }
                        },
                        page = page,
                        endReached = !result.hasNextPage,
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(loading = false, refreshing = false, error = rootMessage(e))
                }
            }
        }
    }

    private fun filtersFor(source: CatalogueSource): FilterList {
        if (filtersSourceId != source.id || filters == null) {
            filters = source.getFilterList()
            filtersSourceId = source.id
            _activeFilterCount.value = activeFilterCount(filters ?: FilterList())
        }
        return filters ?: FilterList()
    }

    /** The sheet's working copy is discarded on Cancel; Apply lands here. */
    fun useFilterSnapshot(snapshot: List<FilterSnapshot>) {
        val entry = selected.value ?: return
        val source = entry.source as? CatalogueSource ?: return
        val current = filtersFor(source)
        restoreFilters(current, snapshot)
        _activeFilterCount.value = activeFilterCount(current)
        reload()
    }

    /** The applied list itself, for the filter sheet's working copy. */
    fun appliedFilters(source: CatalogueSource): FilterList = filtersFor(source)
}

private fun LoadedSource.toChoice(): SourceChoice = SourceChoice(
    id = source.id,
    name = source.name,
    language = language,
    nsfw = extension.contentWarning == ContentWarning.NSFW,
)

/** True when the app should show the 18+ gate; kept for the source sheet. */
internal fun LoadedSource.isNsfw(): Boolean =
    extension.contentWarning == ContentWarning.NSFW
