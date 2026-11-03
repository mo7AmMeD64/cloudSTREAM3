package com.kodastream.app.ui.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.data.CategoryResult
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.data.FavoriteCategoryStore
import com.kodastream.app.data.FavoriteSelection
import com.kodastream.app.data.posterMinWidth
import com.kodastream.app.manga.MangaCategoryStore
import com.kodastream.app.manga.MangaLibraryFilter
import com.kodastream.app.manga.MangaLibrarySort
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.filterLibrary
import com.kodastream.app.manga.key
import com.kodastream.app.manga.sortLibrary
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.PosterSizeButton
import com.kodastream.app.ui.components.SearchEmptyState
import com.kodastream.app.ui.components.SearchField
import com.kodastream.app.ui.library.CategoryChip
import com.kodastream.app.ui.library.CategoryNameDialog
import com.kodastream.app.ui.library.MoveToSheet
import com.kodastream.app.ui.library.SelectionBar
import kotlinx.coroutines.launch

/** Saved-state sentinels; category ids are UUIDs, so neither can collide. */
private const val CATEGORY_ALL = ""
private const val CATEGORY_UNCATEGORIZED = "\u0000none"

private data class CategoryDialogState(val id: String?, val name: String)

private enum class FilterChoice { SOURCE, LANGUAGE }

/** Sort choices in menu order, so the enum and the labels cannot drift. */
private val SORT_LABELS = listOf(
    MangaLibrarySort.TITLE to R.string.manga_library_sort_title,
    MangaLibrarySort.LAST_READ to R.string.manga_library_sort_last_read,
    MangaLibrarySort.LAST_UPDATED to R.string.manga_library_sort_last_updated,
    MangaLibrarySort.DATE_ADDED to R.string.manga_library_sort_date_added,
    MangaLibrarySort.UNREAD to R.string.manga_library_sort_unread,
    MangaLibrarySort.TOTAL_CHAPTERS to R.string.manga_library_sort_total,
)

/**
 * The manga library: own categories, filters and update checks over the
 * `manga_*` stores. Chapter checks run two titles at a time and only for the
 * visible category, so a large library stays responsive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaLibraryScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
    onOpenBrowse: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MangaLibraryViewModel = viewModel()
    val entries by viewModel.entries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoryOf by viewModel.categoryOf.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val check by viewModel.check.collectAsState()

    var posterStep by remember { mutableIntStateOf(MangaPrefs.libraryPosterStep(context)) }
    var sort by remember { mutableStateOf(MangaPrefs.librarySort(context)) }
    var descending by remember { mutableStateOf(MangaPrefs.libraryDescending(context)) }
    var libraryFilter by remember { mutableStateOf(MangaPrefs.libraryFilter(context)) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var selection by remember { mutableStateOf(FavoriteSelection()) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterChoice by remember { mutableStateOf<FilterChoice?>(null) }
    var categoryMenu by remember { mutableStateOf<FavoriteCategory?>(null) }
    var categoryDialog by remember { mutableStateOf<CategoryDialogState?>(null) }
    var deleteCategory by remember { mutableStateOf<FavoriteCategory?>(null) }
    var moveKeys by remember { mutableStateOf<Set<String>?>(null) }
    var showFailures by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val validCategoryIds = remember(categories) { categories.map { it.id }.toSet() }
    val counts = remember(entries, categoryOf, validCategoryIds) {
        val result = HashMap<String?, Int>()
        entries.forEach { entry ->
            val id = categoryOf[entry.key()]?.takeIf { it in validCategoryIds }
            result[id] = (result[id] ?: 0) + 1
        }
        result
    }
    val categoryItems = remember(entries, selectedCategory, categoryOf, validCategoryIds) {
        when (selectedCategory) {
            CATEGORY_ALL -> entries
            CATEGORY_UNCATEGORIZED -> entries.filter { categoryOf[it.key()] !in validCategoryIds }
            else -> entries.filter { categoryOf[it.key()] == selectedCategory }
        }
    }
    val searched = remember(categoryItems, search) {
        val query = search.trim()
        if (query.isEmpty()) {
            categoryItems
        } else {
            categoryItems.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val visible = remember(searched, libraryFilter, sort, descending, stats) {
        sortLibrary(
            entries = filterLibrary(
                entries = searched,
                filter = libraryFilter,
                started = stats.started,
                unread = stats.unread,
            ),
            sort = sort,
            descending = descending,
            unread = stats.unread,
            totals = stats.totals,
            lastUpdated = stats.lastUpdated,
        )
    }
    val multipleLanguages = remember(entries) {
        entries.mapNotNull { it.language.takeIf(String::isNotBlank) }.distinct().size > 1
    }

    LaunchedEffect(categories, counts) {
        if (selectedCategory != CATEGORY_ALL && selectedCategory != CATEGORY_UNCATEGORIZED &&
            categories.none { it.id == selectedCategory }
        ) {
            selectedCategory = CATEGORY_ALL
        }
        if (selectedCategory == CATEGORY_UNCATEGORIZED &&
            (categories.isEmpty() || (counts[null] ?: 0) == 0)
        ) {
            selectedCategory = CATEGORY_ALL
        }
    }
    BackHandler(selection.keys.isNotEmpty()) { selection = FavoriteSelection() }

    // The completion snackbar belongs to a user-initiated run only; the
    // automatic check on open should not interrupt.
    var wasChecking by remember { mutableStateOf(false) }
    LaunchedEffect(check.running) {
        if (wasChecking && !check.running && !check.automatic && check.total > 0) {
            if (check.failures.isEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.manga_library_check_done))
            } else {
                val count = check.failures.size
                val result = snackbar.showSnackbar(
                    message = context.resources.getQuantityString(
                        R.plurals.manga_library_check_failed,
                        count,
                        count,
                    ),
                    actionLabel = context.getString(R.string.manga_library_check_details),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) showFailures = true
            }
        }
        wasChecking = check.running
    }

    fun updateFilter(value: MangaLibraryFilter) {
        libraryFilter = value
        MangaPrefs.setLibraryFilter(context, value)
    }

    fun openNewCategory() {
        if (categories.size >= FavoriteCategoryStore.MAX_CATEGORIES) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.favorites_category_limit))
            }
        } else {
            categoryDialog = CategoryDialogState(id = null, name = "")
        }
    }

    fun moveTo(keys: Set<String>, categoryId: String?, knownName: String? = null) {
        if (keys.isEmpty()) return
        val before = keys.associateWith { categoryOf[it] }
        val name = knownName ?: categories.firstOrNull { it.id == categoryId }?.name
            ?: context.getString(R.string.favorites_category_uncategorized)
        MangaCategoryStore.move(context, keys, categoryId)
        selection = FavoriteSelection()
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.resources.getQuantityString(
                    R.plurals.favorites_moved,
                    keys.size,
                    keys.size,
                    name,
                ),
                actionLabel = context.getString(R.string.history_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                MangaCategoryStore.applyMappings(context, before)
            }
        }
    }

    fun removeSelected() {
        val targets = entries.filter { it.key() in selection.keys }
        if (targets.isEmpty()) {
            selection = FavoriteSelection()
            return
        }
        val before = targets.associate { it.key() to categoryOf[it.key()] }
        MangaLibraryStore.remove(context, targets)
        selection = FavoriteSelection()
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.resources.getQuantityString(
                    R.plurals.manga_library_removed,
                    targets.size,
                    targets.size,
                ),
                actionLabel = context.getString(R.string.history_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                MangaLibraryStore.restore(context, targets)
                MangaCategoryStore.applyMappings(context, before)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding() + 12.dp),
        ) {
            if (selection.keys.isNotEmpty()) {
                SelectionBar(
                    count = selection.keys.size,
                    onMove = { moveKeys = selection.keys },
                    onRemove = { removeSelected() },
                    onClose = { selection = FavoriteSelection() },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.checkUpdates(categoryItems) },
                        enabled = entries.isNotEmpty() && !check.running,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.manga_library_check),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.manga_library_sort_filter),
                                tint = if (
                                    libraryFilter.unreadOnly ||
                                    libraryFilter.startedOnly ||
                                    libraryFilter.completedOnly ||
                                    libraryFilter.sourceName != null ||
                                    libraryFilter.language != null
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        SortFilterMenu(
                            expanded = sortMenu,
                            onDismiss = { sortMenu = false },
                            sort = sort,
                            onSort = {
                                sort = it
                                MangaPrefs.setLibrarySort(context, it)
                            },
                            descending = descending,
                            onDescending = {
                                descending = it
                                MangaPrefs.setLibraryDescending(context, it)
                            },
                            filter = libraryFilter,
                            onFilter = ::updateFilter,
                            onChooseSource = { filterChoice = FilterChoice.SOURCE },
                            onChooseLanguage = { filterChoice = FilterChoice.LANGUAGE },
                        )
                    }
                    PosterSizeButton(
                        step = posterStep,
                        onStepChange = {
                            posterStep = it
                            MangaPrefs.setLibraryPosterStep(context, it)
                        },
                    )
                }
            }

            if (entries.isNotEmpty()) {
                SearchField(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = stringResource(R.string.manga_library_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedCategory == CATEGORY_ALL,
                        onClick = { selectedCategory = CATEGORY_ALL },
                        label = { Text(stringResource(R.string.favorites_category_all)) },
                    )
                    categories.forEach { category ->
                        Box {
                            CategoryChip(
                                name = category.name,
                                count = counts[category.id] ?: 0,
                                selected = selectedCategory == category.id,
                                onClick = { selectedCategory = category.id },
                                onLongClick = { categoryMenu = category },
                                onMenu = { categoryMenu = category },
                            )
                            DropdownMenu(
                                expanded = categoryMenu?.id == category.id,
                                onDismissRequest = { categoryMenu = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorites_category_rename)) },
                                    onClick = {
                                        categoryDialog = CategoryDialogState(category.id, category.name)
                                        categoryMenu = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorites_category_delete)) },
                                    onClick = {
                                        deleteCategory = category
                                        categoryMenu = null
                                    },
                                )
                            }
                        }
                    }
                    if (categories.isNotEmpty() && (counts[null] ?: 0) > 0) {
                        FilterChip(
                            selected = selectedCategory == CATEGORY_UNCATEGORIZED,
                            onClick = { selectedCategory = CATEGORY_UNCATEGORIZED },
                            label = { Text(stringResource(R.string.favorites_category_uncategorized)) },
                        )
                    }
                    AssistChip(
                        onClick = { openNewCategory() },
                        label = { Text(stringResource(R.string.favorites_category_new)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            if (check.running) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(
                                R.string.manga_library_checking,
                                check.done,
                                check.total,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::cancelCheck) {
                            Text(stringResource(R.string.cancel), maxLines = 1)
                        }
                    }
                }
            }

            ExpressivePullToRefresh(
                isRefreshing = check.running,
                onRefresh = { viewModel.checkUpdates(categoryItems) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    entries.isEmpty() -> EmptyBox(
                        title = stringResource(R.string.manga_library_empty_title),
                        hint = stringResource(R.string.manga_library_empty_hint),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Button(onClick = onOpenBrowse) {
                            Text(stringResource(R.string.manga_library_browse))
                        }
                    }

                    categoryItems.isEmpty() -> EmptyBox(
                        title = stringResource(R.string.manga_library_no_results_title),
                        hint = stringResource(R.string.manga_library_no_results_hint),
                        modifier = Modifier.fillMaxSize(),
                    )

                    searched.isEmpty() && search.isNotBlank() -> SearchEmptyState(
                        title = stringResource(R.string.search_no_results),
                        hint = stringResource(R.string.search_no_results_hint),
                        modifier = Modifier.fillMaxSize(),
                    )

                    visible.isEmpty() -> EmptyBox(
                        title = stringResource(R.string.manga_library_no_results_title),
                        hint = stringResource(R.string.manga_library_no_results_hint),
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = posterMinWidth(posterStep)),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.key() }) { entry ->
                            val key = entry.key()
                            MangaPosterCard(
                                sourceId = entry.sourceId,
                                manga = entry.toSManga(),
                                inLibrary = false,
                                unreadCount = stats.unread[key] ?: 0,
                                language = if (multipleLanguages) {
                                    entry.language.takeIf(String::isNotBlank)
                                } else {
                                    null
                                },
                                onClick = {
                                    if (selection.keys.isNotEmpty()) {
                                        selection = selection.toggle(key)
                                    } else {
                                        onOpenDetails(
                                            MangaRef(
                                                sourceId = entry.sourceId,
                                                url = entry.url,
                                                title = entry.title,
                                                thumbnailUrl = entry.thumbnailUrl,
                                            )
                                        )
                                    }
                                },
                                onLongClick = { selection = selection.toggle(key) },
                                selectionMode = selection.keys.isNotEmpty(),
                                selected = key in selection.keys,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        )
    }

    moveKeys?.let { keys ->
        MoveToSheet(
            categories = categories,
            selectedCategoryIds = keys.map { categoryOf[it] }.toSet(),
            onDismiss = { moveKeys = null },
            onMove = { categoryId ->
                moveKeys = null
                moveTo(keys, categoryId)
            },
            onCreateAndMove = { name ->
                moveKeys = null
                when (val result = MangaCategoryStore.create(context, name)) {
                    is CategoryResult.Created -> {
                        selectedCategory = result.category.id
                        moveTo(keys, result.category.id, result.category.name)
                    }
                    CategoryResult.LimitReached -> scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.favorites_category_limit))
                    }
                    else -> Unit
                }
            },
        )
    }

    categoryDialog?.let { dialog ->
        CategoryNameDialog(
            title = stringResource(
                if (dialog.id == null) R.string.favorites_category_new
                else R.string.favorites_category_rename_title
            ),
            initialName = dialog.name,
            confirmLabel = stringResource(
                if (dialog.id == null) R.string.favorites_category_create
                else R.string.favorites_category_rename
            ),
            categories = categories,
            editingId = dialog.id,
            onConfirm = { name ->
                val editingId = dialog.id
                categoryDialog = null
                val result = if (editingId == null) {
                    MangaCategoryStore.create(context, name)
                } else {
                    MangaCategoryStore.rename(context, editingId, name)
                }
                if (result is CategoryResult.Created) selectedCategory = result.category.id
            },
            onDismiss = { categoryDialog = null },
        )
    }

    deleteCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCategory = null },
            title = { Text(stringResource(R.string.favorites_category_delete_title, target.name)) },
            text = {
                Text(
                    stringResource(
                        R.string.manga_category_delete_text,
                        counts[target.id] ?: 0,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCategory = null
                        val keys = entries
                            .filter { categoryOf[it.key()] == target.id }
                            .map { it.key() }
                        MangaCategoryStore.delete(context, target.id)
                        if (selectedCategory == target.id) selectedCategory = CATEGORY_ALL
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.favorites_category_deleted),
                                actionLabel = context.getString(R.string.history_undo),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                MangaCategoryStore.restore(context, target, keys)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.favorites_category_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCategory = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    filterChoice?.let { choice ->
        val options = remember(choice, entries) {
            val values = if (choice == FilterChoice.SOURCE) {
                entries.map { it.sourceName }
            } else {
                entries.map { it.language }
            }
            values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        }
        val current = if (choice == FilterChoice.SOURCE) {
            libraryFilter.sourceName
        } else {
            libraryFilter.language
        }
        AlertDialog(
            onDismissRequest = { filterChoice = null },
            title = {
                Text(
                    stringResource(
                        if (choice == FilterChoice.SOURCE) {
                            R.string.manga_library_filter_source
                        } else {
                            R.string.manga_library_filter_language
                        }
                    )
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FilterOption(
                        label = stringResource(R.string.manga_library_filter_all),
                        selected = current == null,
                        onClick = {
                            updateFilter(
                                if (choice == FilterChoice.SOURCE) {
                                    libraryFilter.copy(sourceName = null)
                                } else {
                                    libraryFilter.copy(language = null)
                                }
                            )
                            filterChoice = null
                        },
                    )
                    options.forEach { option ->
                        FilterOption(
                            label = option,
                            selected = option == current,
                            onClick = {
                                updateFilter(
                                    if (choice == FilterChoice.SOURCE) {
                                        libraryFilter.copy(sourceName = option)
                                    } else {
                                        libraryFilter.copy(language = option)
                                    }
                                )
                                filterChoice = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { filterChoice = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showFailures) {
        AlertDialog(
            onDismissRequest = { showFailures = false },
            title = { Text(stringResource(R.string.manga_library_check_failed_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    check.failures.forEach { failure ->
                        Text(
                            text = failure,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFailures = false }) {
                    Text(stringResource(R.string.manga_library_check_close))
                }
            },
        )
    }
}

@Composable
private fun SortFilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sort: MangaLibrarySort,
    onSort: (MangaLibrarySort) -> Unit,
    descending: Boolean,
    onDescending: (Boolean) -> Unit,
    filter: MangaLibraryFilter,
    onFilter: (MangaLibraryFilter) -> Unit,
    onChooseSource: () -> Unit,
    onChooseLanguage: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SORT_LABELS.forEach { (option, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                trailingIcon = if (sort == option) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = { onSort(option) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.manga_library_descending)) },
            trailingIcon = {
                Switch(
                    checked = descending,
                    onCheckedChange = onDescending,
                )
            },
            onClick = { onDescending(!descending) },
        )
        HorizontalDivider()
        listOf(
            Triple(
                filter.unreadOnly,
                R.string.manga_library_filter_unread,
                { onFilter(filter.copy(unreadOnly = !filter.unreadOnly)) },
            ),
            Triple(
                filter.startedOnly,
                R.string.manga_library_filter_started,
                { onFilter(filter.copy(startedOnly = !filter.startedOnly)) },
            ),
            Triple(
                filter.completedOnly,
                R.string.manga_library_filter_completed,
                { onFilter(filter.copy(completedOnly = !filter.completedOnly)) },
            ),
        ).forEach { (checked, label, toggle) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                trailingIcon = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = toggle,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        R.string.manga_library_filter_source_value,
                        filter.sourceName ?: stringResource(R.string.manga_library_filter_all),
                    )
                )
            },
            onClick = onChooseSource,
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        R.string.manga_library_filter_language_value,
                        filter.language ?: stringResource(R.string.manga_library_filter_all),
                    )
                )
            },
            onClick = onChooseLanguage,
        )
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
