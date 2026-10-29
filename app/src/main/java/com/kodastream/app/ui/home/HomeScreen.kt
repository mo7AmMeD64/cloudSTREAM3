package com.kodastream.app.ui.home

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.LoadingBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.PosterShelfSkeleton
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.SkeletonBox
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.kodastream.app.ui.components.staggeredEnter
import com.kodastream.app.ui.sources.SourceViewModel
import com.kodastream.app.ui.theme.ArtworkStatusBarIcons
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Immutable
data class HomeRow(
    val key: String,
    val title: String,
    val items: List<SearchResponse>,
    val horizontal: Boolean,
    /** Index in `api.mainPage`, so an expanded page can request the same section. */
    val pageIndex: Int,
    /** The last page loaded into [items]. */
    val page: Int,
    val hasNext: Boolean,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _providers = MutableStateFlow(Engine.homeProviders())
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    /** The provider currently loaded; set only by [setSource], never chosen here. */
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * The hero comes from the first shelf that has enough posters, preferring
     * a portrait row. Derived here so composition never walks the shelves,
     * and cached eagerly because the Home screen reads it right away.
     */
    val hero: StateFlow<List<SearchResponse>> = _rows
        .map { rows ->
            rows.sortedBy { it.horizontal }
                .firstOrNull { row -> row.items.count { !it.posterUrl.isNullOrBlank() } >= 3 }
                ?.items
                .orEmpty()
                .filter { !it.posterUrl.isNullOrBlank() }
                .distinctBy { it.url }
                .take(8)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            // Runs again every time an extension is installed, updated or removed.
            PluginManager.revision.collect { _providers.value = Engine.homeProviders() }
        }
    }

    /**
     * Loads the name the source owner resolved. An equal name is a no-op, so a
     * revision that does not change the selection never cancels a running load.
     */
    fun setSource(name: String?) {
        if (name == _selected.value) return
        _selected.value = name
        if (name == null) {
            loadJob?.cancel()
            _rows.value = emptyList()
            _loading.value = false
            _error.value = null
            return
        }
        val api = Engine.provider(name) ?: return
        if (!api.hasMainPage) return
        load(api)
    }

    fun retry() {
        val api = Engine.provider(_selected.value) ?: return
        load(api)
    }

    private fun load(api: MainAPI) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _rows.value = emptyList()

            val pages = api.mainPage
            val results = arrayOfNulls<List<HomeRow>>(pages.size)
            var lastFailure: String? = null
            val gate = Semaphore(if (api.sequentialMainPage) 1 else 3)

            coroutineScope {
                pages.forEachIndexed { index, page ->
                    launch {
                        gate.withPermit {
                            val result = Engine.mainPage(api, page)
                            result.onSuccess { response ->
                                results[index] = response.lists
                                    .filter { it.list.isNotEmpty() }
                                    .mapIndexed { i, list ->
                                        HomeRow(
                                            key = "$index-$i-${list.name}",
                                            title = list.name.ifBlank { page.name },
                                            items = list.list,
                                            horizontal = list.isHorizontalImages,
                                            pageIndex = index,
                                            page = 1,
                                            hasNext = response.hasNext,
                                        )
                                    }
                                _rows.value = results.filterNotNull().flatten()
                            }
                            result.onFailure { lastFailure = it.readableMessage() }
                        }
                    }
                }
            }

            _loading.value = false
            if (_rows.value.isEmpty()) {
                _error.value = lastFailure ?: ""
            }
        }
    }
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenItem: (SearchResponse) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSources: () -> Unit,
    sourceViewModel: SourceViewModel,
    expandedViewModel: ExpandedHomeViewModel,
    onOpenSection: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val providers by viewModel.providers.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val hero by viewModel.hero.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val sections by expandedViewModel.sections.collectAsState()
    val startupDone by PluginManager.startupDone.collectAsState()
    val sourceName by sourceViewModel.home.collectAsState()

    // One direction: the source owner resolves the name, Home only loads it.
    LaunchedEffect(sourceName) { viewModel.setSource(sourceName) }
    // A different provider invalidates every paged section and its page indexes.
    LaunchedEffect(selected) { expandedViewModel.setProvider(selected) }

    Box(Modifier.fillMaxSize()) {
        when {
            providers.isEmpty() && !startupDone -> LoadingBox(Modifier.padding(contentPadding))

            providers.isEmpty() -> EmptyBox(
                title = stringResource(R.string.home_empty_title),
                hint = stringResource(R.string.home_empty_hint),
                modifier = Modifier.padding(contentPadding),
            ) {
                Button(onClick = onOpenExtensions) { Text(stringResource(R.string.open_extensions)) }
            }

            // The owner is still resolving the saved source; do not show a
            // different provider's shelves in the meantime.
            selected == null -> LoadingBox(Modifier.padding(contentPadding))

            else -> ExpressivePullToRefresh(
                isRefreshing = loading,
                onRefresh = {
                    // Fresh source lists make every paged copy stale.
                    expandedViewModel.reset()
                    viewModel.retry()
                },
            ) {
                HomeShelves(
                    selectedSource = selected,
                    rows = rows,
                    hero = hero,
                    loading = loading,
                    error = error,
                    sections = sections,
                    contentPadding = contentPadding,
                    onOpenSources = onOpenSources,
                    onOpenItem = onOpenItem,
                    onRetry = viewModel::retry,
                    onViewMore = { row ->
                        val provider = selected
                        if (provider != null) {
                            expandedViewModel.open(provider, row)
                            onOpenSection()
                        }
                    },
                )
            }
        }
    }
}

/**
 * The shelves themselves, split out of the pull-to-refresh wrapper so the
 * refresh container is not recomposed on every provider switch.
 */
@Composable
private fun HomeShelves(
    selectedSource: String?,
    rows: List<HomeRow>,
    hero: List<SearchResponse>,
    loading: Boolean,
    error: String?,
    sections: Map<String, ExpandedSection>,
    contentPadding: PaddingValues,
    onOpenSources: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onRetry: () -> Unit,
    onViewMore: (HomeRow) -> Unit,
) {
    val chooseSourceLabel = stringResource(R.string.cd_choose_source)
    val listState = rememberLazyListState()
    val topInset = with(LocalDensity.current) { contentPadding.calculateTopPadding().roundToPx() }
    val heroUnderBar by remember(listState, topInset) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "hero" }
                ?.let { it.offset < topInset } == true
        }
    }
    ArtworkStatusBarIcons(overArtwork = heroUnderBar)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                // With the chip row gone, this line names the provider and opens the sheet.
                if (selectedSource != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = chooseSourceLabel, onClick = onOpenSources),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedSource,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (hero.isNotEmpty()) {
            item(key = "hero") {
                HomeHero(items = hero, onOpen = onOpenItem)
            }
        }

        if (rows.isEmpty() && loading) {
            // One hero-sized placeholder at the carousel's real height, then a
            // shelf: the chrome is already on screen and stays put.
            item(key = "skeleton") {
                SkeletonPulseProvider {
                    Column {
                        SkeletonBox(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(480.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                        Spacer(Modifier.height(24.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .size(width = 160.dp, height = 24.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                        Spacer(Modifier.height(12.dp))
                        PosterShelfSkeleton()
                    }
                }
            }
        } else if (rows.isEmpty()) {
            item(key = "error") {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    ErrorBox(
                        message = error?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.nothing_found),
                        onRetry = onRetry,
                    )
                }
            }
        }

        itemsIndexed(
            items = rows,
            key = { _, row -> row.key },
            contentType = { _, _ -> "shelf" },
        ) { index, row ->
            // The expanded screen turns hasNext off when a page ends or fails,
            // and that hides the button here without reloading Home.
            val canViewMore = row.hasNext &&
                sections[sectionKey(selectedSource.orEmpty(), row.key)]?.hasNext != false
            val viewMore: (() -> Unit)? = if (canViewMore) ({ onViewMore(row) }) else null
            // The first three shelves rise in once; later ones just appear, and
            // a provider switch reuses the already-settled rows.
            if (index < 3) {
                val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = entrance,
                    enter = staggeredEnter(index),
                ) {
                    Shelf(row = row, onOpenItem = onOpenItem, onViewMore = viewMore)
                }
            } else {
                Shelf(row = row, onOpenItem = onOpenItem, onViewMore = viewMore)
            }
        }

        if (rows.isNotEmpty() && loading) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    row: HomeRow,
    onOpenItem: (SearchResponse) -> Unit,
    onViewMore: (() -> Unit)?,
) {
    Column {
        SectionHeader(row.title, onViewMore = onViewMore)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // No keys here: providers sometimes return the same item twice.
            items(
                items = row.items,
                contentType = { if (row.horizontal) "horizontal" else "poster" },
            ) { item ->
                PosterCard(
                    item = item,
                    horizontal = row.horizontal,
                    onClick = { onOpenItem(item) },
                    cardWidth = if (row.horizontal) 220.dp else 128.dp,
                )
            }
        }
    }
}
