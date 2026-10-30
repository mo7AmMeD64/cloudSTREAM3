package com.kodastream.app.ui.search

import android.app.Application
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.SearchHistoryStore
import com.kodastream.app.engine.Engine
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.sources.SourceViewModel
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SearchGroup(val apiName: String, val items: List<SearchResponse>)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _groups = MutableStateFlow<List<SearchGroup>>(emptyList())
    val groups: StateFlow<List<SearchGroup>> = _groups.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** True once a search was run, so "no results" is not shown before the first one. */
    private val _searched = MutableStateFlow(false)
    val searched: StateFlow<Boolean> = _searched.asStateFlow()

    /** Null until the stored list loads. */
    val history: StateFlow<List<String>?> = SearchHistoryStore.entries

    private val _pages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pages: StateFlow<Map<String, Int>> = _pages.asStateFlow()

    private val _lastPages = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val lastPages: StateFlow<Map<String, Boolean>> = _lastPages.asStateFlow()

    private val _loadingProviders = MutableStateFlow<Set<String>>(emptySet())
    val loadingProviders: StateFlow<Set<String>> = _loadingProviders.asStateFlow()

    private var job: Job? = null

    init {
        SearchHistoryStore.current(getApplication())
    }

    /** [scope] narrows the search to one provider by name; null searches every provider. */
    fun submit(query: String, scope: String? = null, reset: Boolean = true) {
        val text = query.trim()
        if (text.isEmpty()) return

        if (reset) {
            // Search history is not watch history, but the same pause switch stops
            // both from being recorded. Existing entries stay.
            if (!AppPrefs.pauseHistory(getApplication())) {
                SearchHistoryStore.add(getApplication(), text)
            }
            job?.cancel()
            _groups.value = emptyList()
            _lastPages.value = emptyMap()
            _pages.value = emptyMap()
        } else {
            if (_searching.value) return
        }

        val all = Engine.providers()
        val inScope = if (scope == null) all else all.filter { it.name == scope }
        val providers = if (reset) {
            inScope
        } else {
            inScope.filter { api -> _lastPages.value[api.name] == true }
        }
        if (providers.isEmpty()) return

        val nextPages = _pages.value.toMutableMap()
        providers.forEach { api ->
            nextPages[api.name] = if (reset) 1 else (nextPages[api.name] ?: 1) + 1
        }
        _pages.value = nextPages

        val providerOrder = all.mapIndexed { index, api -> api.name to index }.toMap()

        job = viewModelScope.launch {
            _searching.value = true
            _searched.value = true
            _loadingProviders.value = providers.map { it.name }.toSet()

            val gate = Semaphore(4)
            coroutineScope {
                providers.forEach { api ->
                    launch {
                        gate.withPermit {
                            val page = _pages.value[api.name] ?: 1
                            // A provider that fails or has no search is simply skipped.
                            Engine.search(api, text, page)
                                .onSuccess { result ->
                                    val newItems = result.items
                                    _lastPages.update { it + (api.name to (result.hasNext && newItems.isNotEmpty())) }
                                    if (newItems.isNotEmpty()) {
                                        _groups.update { current ->
                                            mergeSearchGroup(current, api.name, newItems)
                                                .sortedBy { providerOrder[it.apiName] ?: Int.MAX_VALUE }
                                        }
                                    }
                                }
                                .onFailure {
                                    _lastPages.update { it + (api.name to false) }
                                }
                            _loadingProviders.update { it - api.name }
                        }
                    }
                }
            }
            _searching.value = false
            _loadingProviders.value = emptySet()
        }
    }

    fun removeHistory(entry: String) {
        SearchHistoryStore.remove(getApplication(), entry)
    }

    fun clearHistory() {
        SearchHistoryStore.clear(getApplication())
    }

    fun clear() {
        job?.cancel()
        _groups.value = emptyList()
        _searching.value = false
        _searched.value = false
        _pages.value = emptyMap()
        _lastPages.value = emptyMap()
        _loadingProviders.value = emptySet()
    }
}

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenItem: (SearchResponse) -> Unit,
    sourceViewModel: SourceViewModel,
    viewModel: SearchViewModel = viewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searched by viewModel.searched.collectAsState()
    val history by viewModel.history.collectAsState()
    val lastPages by viewModel.lastPages.collectAsState()
    val loadingProviders by viewModel.loadingProviders.collectAsState()
    val scope by sourceViewModel.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    // The scope lives in the shared holder so the source sheet can change it.
    var appliedScope by remember { mutableStateOf(scope) }
    LaunchedEffect(scope) {
        if (scope != appliedScope) {
            appliedScope = scope
            if (query.isNotBlank()) viewModel.submit(query, scope) else viewModel.clear()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        viewModel.clear()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            supportingText = {
                Text(
                    text = stringResource(
                        R.string.searching_in,
                        scope ?: stringResource(R.string.search_scope_all),
                    )
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submit(query, scope) }),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Box(Modifier.fillMaxSize()) {
            when {
                groups.isEmpty() && searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(44.dp))
                }

                groups.isEmpty() && searched -> EmptyBox(
                    title = stringResource(R.string.search_no_results),
                    hint = stringResource(R.string.search_no_results_hint),
                )

                !searched && !history.isNullOrEmpty() -> SearchHistory(
                    entries = history.orEmpty().filter { entry ->
                        query.isBlank() || entry.contains(query.trim(), ignoreCase = true)
                    },
                    contentPadding = contentPadding,
                    onPick = { entry ->
                        query = entry
                        viewModel.submit(entry, scope)
                    },
                    onRemove = viewModel::removeHistory,
                    onClearAll = viewModel::clearHistory,
                )

                groups.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.search_idle_title),
                    hint = stringResource(R.string.search_idle_hint),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.apiName }) { group ->
                        val isLoading = group.apiName in loadingProviders
                        Column {
                            SectionHeader(
                                title = if (isLoading) "${group.apiName} ..." else group.apiName,
                                modifier = if (isLoading) Modifier.alpha(0.5f) else Modifier,
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(group.items) { item ->
                                    PosterCard(
                                        item = item,
                                        onClick = { onOpenItem(item) },
                                        cardWidth = 128.dp,
                                    )
                                }
                            }
                        }
                    }
                    if (searching) {
                        item(key = "searching") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(Modifier.size(32.dp))
                            }
                        }
                    } else if (shouldShowLoadMore(lastPages)) {
                        item(key = "load_more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                TextButton(onClick = { viewModel.submit(query, scope, reset = false) }) {
                                    Text(stringResource(R.string.search_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Recent searches: a suggestion list while nothing has been searched yet. */
@Composable
private fun SearchHistory(
    entries: List<String>,
    contentPadding: PaddingValues,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
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
                        color = MaterialTheme.colorScheme.onBackground,
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
                        .clickable(
                            onClickLabel = stringResource(R.string.cd_search_history_item, entry),
                            onClick = { onPick(entry) },
                        )
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_search_history_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
