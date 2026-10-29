package com.kodastream.app.ui.library

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.LibraryStore.LibraryItem
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.posterMinWidth
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.PosterSizeButton
import com.kodastream.app.ui.components.SEARCH_FIELD_MIN_ITEMS
import com.kodastream.app.ui.components.SearchEmptyState
import com.kodastream.app.ui.components.SearchField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The shelves the library keeps. */
enum class LibrarySection { FAVORITES, WATCHLIST, DOWNLOADS }

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val _favorites = MutableStateFlow<List<LibraryItem>>(emptyList())
    val favorites: StateFlow<List<LibraryItem>> = _favorites.asStateFlow()

    private val _watchlist = MutableStateFlow<List<LibraryItem>>(emptyList())
    val watchlist: StateFlow<List<LibraryItem>> = _watchlist.asStateFlow()

    init {
        viewModelScope.launch {
            // Fires once for the initial read too, so the lists arrive at all.
            LibraryStore.revision.collect { reload() }
        }
    }

    private fun reload() {
        val context = getApplication<Application>()
        _favorites.value = LibraryStore.favorites(context)
        _watchlist.value = LibraryStore.watchlist(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val favorites by viewModel.favorites.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    var section by rememberSaveable { mutableStateOf(LibrarySection.FAVORITES) }
    var posterStep by remember { mutableIntStateOf(AppPrefs.posterSizeStep(context)) }

    // "View" from a download snackbar lands here, on the Downloads tab.
    LaunchedEffect(Unit) {
        AppEvents.pendingLibrarySection.collect { pending ->
            if (pending != null) {
                section = LibrarySection.entries.firstOrNull { it.name == pending } ?: section
                AppEvents.pendingLibrarySection.value = null
            }
        }
    }
    var filter by remember { mutableStateOf("") }

    val all = if (section == LibrarySection.FAVORITES) favorites else watchlist
    val items = if (filter.isBlank()) all else all.filter { it.name.contains(filter, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding() + 12.dp),
    ) {
        // The section switch sits in a row of its own so the filter can slide
        // in under it without moving anything when it is not needed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = section == LibrarySection.FAVORITES,
                    onClick = { section = LibrarySection.FAVORITES },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.library_favorites))
                }
                SegmentedButton(
                    selected = section == LibrarySection.WATCHLIST,
                    onClick = { section = LibrarySection.WATCHLIST },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) {
                    Text(stringResource(R.string.library_watchlist))
                }
                SegmentedButton(
                    selected = section == LibrarySection.DOWNLOADS,
                    onClick = { section = LibrarySection.DOWNLOADS },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) {
                    Text(stringResource(R.string.nav_downloads))
                }
            }
            if (section != LibrarySection.DOWNLOADS) {
                PosterSizeButton(
                    step = posterStep,
                    onStepChange = { step ->
                        posterStep = step
                        AppPrefs.setPosterSizeStep(context, step)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = all.size >= SEARCH_FIELD_MIN_ITEMS,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SearchField(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = stringResource(R.string.library_filter_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (section == LibrarySection.DOWNLOADS) {
            DownloadsSection(
                contentPadding = contentPadding,
                onOpenItem = onOpenItem,
                onResume = onResume,
            )
            return
        }

        when {
            all.isEmpty() -> EmptyBox(
                title = stringResource(R.string.library_empty_title),
                hint = stringResource(
                    if (section == LibrarySection.FAVORITES) {
                        R.string.library_empty_hint_favorites
                    } else {
                        R.string.library_empty_hint_watchlist
                    }
                ),
                modifier = Modifier.weight(1f),
            )

            items.isEmpty() -> SearchEmptyState(
                title = stringResource(R.string.search_no_results),
                hint = stringResource(R.string.search_no_results_hint),
                modifier = Modifier.weight(1f),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = posterMinWidth(posterStep)),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { "${it.apiName}|${it.url}" }) { item ->
                    PosterCard(
                        name = item.name,
                        posterUrl = item.posterUrl,
                        posterHeaders = item.posterHeaders,
                        score10 = item.score10,
                        onClick = {
                            onOpenItem(
                                DetailsArgs(
                                    apiName = item.apiName,
                                    url = item.url,
                                    title = item.name,
                                    posterUrl = item.posterUrl,
                                    posterHeaders = item.posterHeaders,
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    )
                }
            }
        }
    }
}
