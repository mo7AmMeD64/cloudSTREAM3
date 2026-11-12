package com.kodastream.app.ui.manga

import eu.kanade.tachiyomi.source.online.HttpSource
import com.kodastream.app.manga.reader.MangaWebViewActivity
import com.kodastream.app.manga.extension.MangaSourceManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.key
import com.kodastream.app.manga.mangaKey
import com.kodastream.app.manga.titleOrEmpty
import com.kodastream.app.manga.urlOrNull
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.SearchField
import com.kodastream.app.ui.components.SectionHeader

/** Global manga search: one shelf per source, each with its own state. */
@Composable
fun MangaSearchScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MangaSearchViewModel = viewModel()
    val sources by viewModel.sources.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searched by viewModel.searched.collectAsState()
    val history by viewModel.history.collectAsState()

    // A source behind a Cloudflare check: solve it in the in-app browser, then search again.
    val webViewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.searchAgain() }

    var query by rememberSaveable { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }

    // Leaving the screen cancels an in-flight search; the shelves already
    // filled stay, so coming back does not flash empty.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.cancel() }
    }

    val pinned = MangaPrefs.pinnedSources(context)
    val visible = remember(sources) { viewModel.visibleSources() }
    val showSearchAll = pinned.isEmpty() && visible.size > 1

    val revision by MangaLibraryStore.revision.collectAsState()
    val libraryKeys = remember(revision) {
        MangaLibraryStore.entries(context).mapTo(HashSet()) { it.key() }
    }

    fun runSearch(allSources: Boolean) {
        viewModel.submit(query, allSources)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Text(
            text = stringResource(R.string.nav_search),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                query = query,
                onQueryChange = { text ->
                    query = text
                    if (text.isBlank()) viewModel.clear()
                },
                placeholder = stringResource(R.string.manga_search_empty_title),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { runSearch(false) },
                enabled = query.isNotBlank(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.manga_search_submit))
            }
        }

        if (showSearchAll) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manga_search_scope_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (visible.size > 10) confirmAll = true else runSearch(true)
                    },
                    enabled = query.isNotBlank(),
                ) {
                    Text(stringResource(R.string.manga_search_all_sources, visible.size))
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                shelves.isEmpty() && searching -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator(Modifier.size(44.dp)) }

                shelves.isEmpty() && searched -> EmptyBox(
                    title = stringResource(R.string.nothing_found),
                    hint = null,
                )

                !searched && !history.isNullOrEmpty() -> MangaSearchHistory(
                    entries = history.orEmpty(),
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onPick = {
                        query = it
                        viewModel.submit(it)
                    },
                    onRemove = viewModel::removeHistory,
                    onClearAll = viewModel::clearHistory,
                )

                shelves.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.manga_search_empty_title),
                    hint = stringResource(R.string.manga_search_empty_hint),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shelves, key = { it.sourceId }) { shelf ->
                        Column {
                            SectionHeader(title = shelf.sourceName)
                            when {
                                shelf.loading -> Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) { LoadingIndicator(Modifier.size(28.dp)) }

                                shelf.error != null -> Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (shelf.blocked) {
                                            stringResource(R.string.manga_cloudflare_blocked)
                                        } else {
                                            shelf.error ?: ""
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val http = if (shelf.blocked) {
                                        MangaSourceManager.get(context).sourceById(shelf.sourceId) as? HttpSource
                                    } else {
                                        null
                                    }
                                    if (http != null) {
                                        TextButton(
                                            onClick = {
                                                webViewLauncher.launch(
                                                    MangaWebViewActivity.intent(context, http.baseUrl, http.id),
                                                )
                                            },
                                        ) {
                                            Text(stringResource(R.string.manga_reader_open_webview))
                                        }
                                    }
                                }

                                shelf.items.isEmpty() -> Text(
                                    text = stringResource(R.string.nothing_found),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )

                                else -> LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(shelf.items, key = { it.urlOrNull().orEmpty() }) { manga ->
                                        MangaPosterCard(
                                            sourceId = shelf.sourceId,
                                            manga = manga,
                                            inLibrary = libraryKeys.contains(
                                                mangaKey(
                                                    shelf.sourceId,
                                                    manga.urlOrNull().orEmpty(),
                                                )
                                            ),
                                            onClick = {
                                                onOpenDetails(
                                                    MangaRef(
                                                        sourceId = shelf.sourceId,
                                                        url = manga.urlOrNull().orEmpty(),
                                                        title = manga.titleOrEmpty(),
                                                        thumbnailUrl = manga.thumbnail_url,
                                                    )
                                                )
                                            },
                                            cardWidth = 128.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (searching) {
                        item(key = "searching") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { LoadingIndicator(Modifier.size(32.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.manga_search_all_title)) },
            text = {
                Text(stringResource(R.string.manga_search_all_message, visible.size))
            },
            confirmButton = {
                Button(onClick = {
                    confirmAll = false
                    runSearch(true)
                }) { Text(stringResource(R.string.manga_filter_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MangaSearchHistory(
    entries: List<String>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        ) {
            item(key = "history-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.search_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearAll) {
                        Text(stringResource(R.string.search_history_clear))
                    }
                }
            }
            items(entries, key = { it }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = { onPick(entry) })
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    )
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
