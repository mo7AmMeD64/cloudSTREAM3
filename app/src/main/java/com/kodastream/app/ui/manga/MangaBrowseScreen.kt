package com.kodastream.app.ui.manga

import eu.kanade.tachiyomi.source.online.HttpSource
import com.kodastream.app.manga.reader.MangaWebViewActivity
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Public
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.data.posterMinWidth
import com.kodastream.app.manga.FilterSnapshot
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.manga.key
import com.kodastream.app.manga.SourceChoice
import com.kodastream.app.manga.extension.LoadedSource
import com.kodastream.app.manga.hasUsableFilters
import com.kodastream.app.manga.store.MangaStoreRepository
import com.kodastream.app.manga.titleOrEmpty
import com.kodastream.app.manga.urlOrNull
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.PosterSizeButton
import com.kodastream.app.ui.components.SearchField
import com.kodastream.app.ui.components.SkeletonPulseProvider
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Browse: one source at a time, Popular or Latest, with the source's own
 * search and filters. The grid loads the next page when the last five items
 * are visible and merges by URL, so a source that repeats results cannot
 * duplicate a card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaBrowseScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
    onOpenExtensions: () -> Unit,
    pendingGenre: Pair<Long, String>? = null,
    onPendingGenreConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MangaBrowseViewModel = viewModel()

    val sources by viewModel.sources.collectAsState()
    val loadingSources by viewModel.loadingSources.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val state by viewModel.state.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val query by viewModel.query.collectAsState()
    val filterCount by viewModel.activeFilterCount.collectAsState()

    // The in-app browser for a Cloudflare check; coming back reloads the list
    // with the cookie the check just earned.
    val webViewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.reload() }
    val httpSource = selected?.source as? HttpSource
    val openWebView: (() -> Unit)? = httpSource?.let { http ->
        { webViewLauncher.launch(MangaWebViewActivity.intent(context, http.baseUrl, http.id)) }
    }

    var posterStep by rememberSaveable { mutableStateOf(MangaPrefs.browsePosterStep(context)) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    var pinned by remember { mutableStateOf(MangaPrefs.pinnedSources(context)) }

    val revision by MangaLibraryStore.revision.collectAsState()
    val libraryKeys = remember(revision) {
        MangaLibraryStore.entries(context).mapTo(HashSet()) { it.key() }
    }

    val catalog by MangaStoreRepository.get(context).available.collectAsState()
    val iconByPackage = remember(catalog) { catalog.associate { it.pkgName to it.iconUrl } }

    val catalogue = selected?.source as? CatalogueSource
    val emptySource = sources.isEmpty() && !loadingSources

    LaunchedEffect(pendingGenre) {
        val (sourceId, genre) = pendingGenre ?: return@LaunchedEffect
        if (sourceId > 0L && sourceId != selected?.source?.id) viewModel.selectSource(sourceId)
        viewModel.setQuery(genre)
        onPendingGenreConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_browse),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (openWebView != null) {
                IconButton(onClick = openWebView) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.manga_reader_open_webview),
                    )
                }
            }
            PosterSizeButton(
                step = posterStep,
                onStepChange = {
                    posterStep = it
                    MangaPrefs.setBrowsePosterStep(context, it)
                },
            )
        }

        SearchField(
            query = query,
            onQueryChange = viewModel::setQuery,
            placeholder = stringResource(
                R.string.manga_search_in_source,
                selected?.source?.name ?: stringResource(R.string.manga_sources_title),
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { showSources = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selected?.source?.name ?: stringResource(R.string.manga_sources_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.ExpandMore, contentDescription = null)
            }
            OutlinedButton(
                onClick = { showFilters = true },
                enabled = catalogue != null && hasUsableFilters(catalogue.getFilterList()),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (filterCount > 0) {
                        stringResource(R.string.manga_filter_active, filterCount)
                    } else {
                        stringResource(R.string.manga_filters)
                    },
                    maxLines = 1,
                )
            }
        }

        if (catalogue?.supportsLatest == true) {
            val segments = listOf(
                stringResource(R.string.manga_popular),
                stringResource(R.string.manga_latest),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 40.dp),
            ) {
                segments.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { viewModel.setTab(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                loadingSources && sources.isEmpty() -> MangaGridSkeleton(posterStep)
                emptySource -> EmptyBox(
                    title = stringResource(R.string.manga_browse_no_sources_title),
                    hint = stringResource(R.string.manga_browse_no_sources_hint),
                ) {
                    Button(onClick = onOpenExtensions) {
                        Text(stringResource(R.string.manga_browse_open_extensions))
                    }
                }

                state.initialLoading -> MangaGridSkeleton(posterStep)
                state.error != null && state.items.isEmpty() -> ErrorBox(
                    message = if (state.blocked) {
                        stringResource(R.string.manga_cloudflare_blocked)
                    } else {
                        state.error ?: ""
                    },
                    onRetry = viewModel::retry,
                    secondaryLabel = if (state.blocked && openWebView != null) {
                        stringResource(R.string.manga_reader_open_webview)
                    } else {
                        null
                    },
                    onSecondary = if (state.blocked) openWebView else null,
                )

                state.items.isEmpty() && !state.loading -> EmptyBox(
                    title = stringResource(R.string.nothing_found),
                    hint = null,
                )

                else -> MangaResultGrid(
                    viewModel = viewModel,
                    state = state,
                    sourceId = selected?.source?.id ?: 0L,
                    libraryKeys = libraryKeys,
                    posterStep = posterStep,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onOpenDetails = onOpenDetails,
                )
            }
        }
    }

    if (showSources) {
        MangaSourceSheet(
            sources = sources.map { it.toChoice() },
            selectedId = selected?.source?.id,
            pinnedIds = pinned,
            iconUrlFor = { id ->
                sources.firstOrNull { it.source.id == id }
                    ?.let { iconByPackage[it.extension.pkgName] }
            },
            onSelect = { choice ->
                showSources = false
                viewModel.selectSource(choice.id)
            },
            onTogglePin = { choice, isPinned ->
                MangaPrefs.setPinned(context, choice.id, isPinned)
                pinned = MangaPrefs.pinnedSources(context)
            },
            onDismiss = { showSources = false },
        )
    }

    if (showFilters && catalogue != null) {
        MangaFilterSheet(
            source = catalogue,
            applied = viewModel.appliedFilters(catalogue),
            onApply = { snapshot: List<FilterSnapshot> ->
                showFilters = false
                viewModel.useFilterSnapshot(snapshot)
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun MangaResultGrid(
    viewModel: MangaBrowseViewModel,
    state: MangaBrowseState,
    sourceId: Long,
    libraryKeys: Set<String>,
    posterStep: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenDetails: (MangaRef) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= gridState.layoutInfo.totalItemsCount - 5
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state.loading, state.endReached) {
        if (shouldLoadMore) viewModel.loadMore()
    }
    ExpressivePullToRefresh(
        isRefreshing = state.refreshing,
        onRefresh = {
            // Pull-to-refresh is a scroll gesture: snap to the top first, so
            // the new first page is what the user sees.
            scope.launch { gridState.scrollToItem(0) }
            viewModel.refresh()
        },
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = posterMinWidth(posterStep)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomPadding + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.urlOrNull().orEmpty() }) { manga ->
                MangaPosterCard(
                    sourceId = sourceId,
                    manga = manga,
                    inLibrary = libraryKeys.contains(
                        com.kodastream.app.manga.mangaKey(
                            sourceId,
                            manga.urlOrNull().orEmpty(),
                        )
                    ),
                    onClick = {
                        onOpenDetails(
                            MangaRef(
                                sourceId = sourceId,
                                url = manga.urlOrNull().orEmpty(),
                                title = manga.titleOrEmpty(),
                                thumbnailUrl = manga.thumbnail_url,
                            )
                        )
                    },
                )
            }
            if (state.loadingMore || state.error != null || state.endReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            state.loadingMore -> Text(
                                text = stringResource(R.string.manga_loading_more),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            state.error != null -> TextButton(onClick = viewModel::retry) {
                                Text(stringResource(R.string.manga_retry_more))
                            }

                            else -> Text(
                                text = stringResource(R.string.nothing_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaGridSkeleton(posterStep: Int) {
    SkeletonPulseProvider {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = posterMinWidth(posterStep)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(8) { MangaPosterSkeleton() }
        }
    }
}

private fun LoadedSource.toChoice(): SourceChoice = SourceChoice(
    id = source.id,
    name = source.name,
    language = language,
    nsfw = isNsfw(),
)
