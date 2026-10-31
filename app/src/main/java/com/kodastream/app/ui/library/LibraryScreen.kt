package com.kodastream.app.ui.library

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.CategoryResult
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.data.FavoriteCategoryStore
import com.kodastream.app.data.FavoriteSelection
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

/** Tabs in display order with their labels, so an index and a count cannot disagree. */
private val LIBRARY_TABS = listOf(
    LibrarySection.FAVORITES to R.string.library_favorites,
    LibrarySection.WATCHLIST to R.string.library_watchlist,
    LibrarySection.DOWNLOADS to R.string.nav_downloads,
)

/** Saved-state sentinels; category ids are UUIDs, so neither can collide. */
private const val CATEGORY_ALL = ""
private const val CATEGORY_UNCATEGORIZED = "\u0000none"

private data class CategoryDialogState(val id: String?, val name: String)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val _favorites = MutableStateFlow<List<LibraryItem>>(emptyList())
    val favorites: StateFlow<List<LibraryItem>> = _favorites.asStateFlow()

    private val _watchlist = MutableStateFlow<List<LibraryItem>>(emptyList())
    val watchlist: StateFlow<List<LibraryItem>> = _watchlist.asStateFlow()

    private val _categories = MutableStateFlow<List<FavoriteCategory>>(emptyList())
    val categories: StateFlow<List<FavoriteCategory>> = _categories.asStateFlow()

    private val _categoryOf = MutableStateFlow<Map<String, String>>(emptyMap())
    val categoryOf: StateFlow<Map<String, String>> = _categoryOf.asStateFlow()

    init {
        // Orphaned mappings are ignored everywhere; this just drops them.
        FavoriteCategoryStore.prune(getApplication())
        viewModelScope.launch {
            // Fires once for the initial read too, so the lists arrive at all.
            LibraryStore.revision.collect { reload() }
        }
    }

    private fun reload() {
        val context = getApplication<Application>()
        _favorites.value = LibraryStore.favorites(context)
        _watchlist.value = LibraryStore.watchlist(context)
        _categories.value = FavoriteCategoryStore.categories(context)
        _categoryOf.value = FavoriteCategoryStore.mappings(context)
    }
}

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
    val categories by viewModel.categories.collectAsState()
    val categoryOf by viewModel.categoryOf.collectAsState()
    val revision by LibraryStore.revision.collectAsState()
    var section by rememberSaveable { mutableStateOf(LibrarySection.FAVORITES) }
    var posterStep by remember { mutableIntStateOf(AppPrefs.posterSizeStep(context)) }
    var selectedCategory by rememberSaveable { mutableStateOf(CATEGORY_ALL) }
    var selection by remember { mutableStateOf(FavoriteSelection()) }
    var categoryMenu by remember { mutableStateOf<FavoriteCategory?>(null) }
    var categoryDialog by remember { mutableStateOf<CategoryDialogState?>(null) }
    var deleteCategory by remember { mutableStateOf<FavoriteCategory?>(null) }
    var moveKeys by remember { mutableStateOf<Set<String>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    val counts = remember(revision, favorites, categories, categoryOf) {
        val valid = categories.map { it.id }.toSet()
        val result = mutableMapOf<String, Int>()
        favorites.forEach { item ->
            val id = categoryOf[LibraryStore.keyOf(item)]
            if (id != null && id in valid) result[id] = (result[id] ?: 0) + 1
        }
        result
    }
    val uncategorizedCount = remember(revision, favorites, categories, categoryOf) {
        val valid = categories.map { it.id }.toSet()
        favorites.count { item ->
            val id = categoryOf[LibraryStore.keyOf(item)]
            id == null || id !in valid
        }
    }
    val sectionItems = if (section == LibrarySection.FAVORITES) favorites else watchlist
    val visible = remember(revision, section, selectedCategory, favorites, watchlist, categories, categoryOf) {
        when {
            section != LibrarySection.FAVORITES -> watchlist
            selectedCategory == CATEGORY_ALL -> favorites
            else -> {
                val valid = categories.map { it.id }.toSet()
                favorites.filter { item ->
                    val id = categoryOf[LibraryStore.keyOf(item)]?.takeIf { it in valid }
                    if (selectedCategory == CATEGORY_UNCATEGORIZED) id == null else id == selectedCategory
                }
            }
        }
    }
    val items = remember(visible, filter) {
        if (filter.isBlank()) visible else visible.filter { it.name.contains(filter, ignoreCase = true) }
    }

    LaunchedEffect(section) {
        selection = FavoriteSelection()
        categoryMenu = null
        categoryDialog = null
        deleteCategory = null
        moveKeys = null
    }
    // Keyed on the lists, not the selection: a freshly created category is
    // selected before the reload lands, and must not be reset in between.
    LaunchedEffect(categories, uncategorizedCount) {
        if (selectedCategory != CATEGORY_ALL && selectedCategory != CATEGORY_UNCATEGORIZED &&
            categories.none { it.id == selectedCategory }
        ) {
            selectedCategory = CATEGORY_ALL
        }
        if (selectedCategory == CATEGORY_UNCATEGORIZED &&
            (categories.isEmpty() || uncategorizedCount == 0)
        ) {
            selectedCategory = CATEGORY_ALL
        }
    }
    BackHandler(selection.keys.isNotEmpty()) { selection = FavoriteSelection() }

    fun openNewCategory() {
        if (categories.size >= FavoriteCategoryStore.MAX_CATEGORIES) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.favorites_category_limit)) }
        } else {
            categoryDialog = CategoryDialogState(id = null, name = "")
        }
    }

    fun moveTo(keys: Set<String>, categoryId: String?, knownName: String? = null) {
        if (keys.isEmpty()) return
        val before = keys.associateWith { categoryOf[it] }
        val name = knownName ?: categories.firstOrNull { it.id == categoryId }?.name
            ?: context.getString(R.string.favorites_category_uncategorized)
        FavoriteCategoryStore.move(context, keys, categoryId)
        selection = FavoriteSelection()
        scope.launch {
            val message = context.resources.getQuantityString(
                R.plurals.favorites_moved,
                keys.size,
                keys.size,
                name,
            )
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.history_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                FavoriteCategoryStore.applyMappings(context, before)
            }
        }
    }

    fun removeSelected() {
        val itemsToRemove = favorites.filter { LibraryStore.keyOf(it) in selection.keys }
        if (itemsToRemove.isEmpty()) {
            selection = FavoriteSelection()
            return
        }
        val before = itemsToRemove.associate {
            LibraryStore.keyOf(it) to categoryOf[LibraryStore.keyOf(it)]
        }
        LibraryStore.remove(context, itemsToRemove)
        selection = FavoriteSelection()
        scope.launch {
            val message = context.resources.getQuantityString(
                R.plurals.favorites_removed,
                itemsToRemove.size,
                itemsToRemove.size,
            )
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.history_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                LibraryStore.restore(context, itemsToRemove)
                FavoriteCategoryStore.applyMappings(context, before)
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
                // The section switch sits in a row of its own so the filter can slide
                // in under it without moving anything when it is not needed. Three
                // labels do not fit beside the side bar, so the chips scroll instead.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LIBRARY_TABS.forEach { (tab, label) ->
                            FilterChip(
                                selected = section == tab,
                                onClick = { section = tab },
                                label = {
                                    Text(
                                        text = stringResource(label),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
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

                if (section == LibrarySection.FAVORITES) {
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
                        if (categories.isNotEmpty() && uncategorizedCount > 0) {
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
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = sectionItems.size >= SEARCH_FIELD_MIN_ITEMS,
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
                return@Column
            }

            when {
                sectionItems.isEmpty() -> EmptyBox(
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

                visible.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.favorites_empty_title),
                    hint = stringResource(R.string.favorites_empty_hint),
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
                    items(items, key = { LibraryStore.keyOf(it) }) { item ->
                        val itemKey = LibraryStore.keyOf(item)
                        PosterCard(
                            name = item.name,
                            posterUrl = item.posterUrl,
                            posterHeaders = item.posterHeaders,
                            score10 = item.score10,
                            onClick = {
                                if (selection.keys.isNotEmpty()) {
                                    selection = selection.toggle(itemKey)
                                } else {
                                    onOpenItem(
                                        DetailsArgs(
                                            apiName = item.apiName,
                                            url = item.url,
                                            title = item.name,
                                            posterUrl = item.posterUrl,
                                            posterHeaders = item.posterHeaders,
                                        )
                                    )
                                }
                            },
                            onLongClick = if (section == LibrarySection.FAVORITES) {
                                { selection = selection.toggle(itemKey) }
                            } else {
                                null
                            },
                            selected = itemKey in selection.keys,
                            selectionMode = selection.keys.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                        )
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
        val currentCategories = keys.map { categoryOf[it] }.toSet()
        MoveToSheet(
            categories = categories,
            selectedCategoryIds = currentCategories,
            onDismiss = { moveKeys = null },
            onMove = { categoryId ->
                moveKeys = null
                moveTo(keys, categoryId)
            },
            onCreateAndMove = { name ->
                moveKeys = null
                when (val result = FavoriteCategoryStore.create(context, name)) {
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
                    FavoriteCategoryStore.create(context, name)
                } else {
                    FavoriteCategoryStore.rename(context, editingId, name)
                }
                if (result is CategoryResult.Created) selectedCategory = result.category.id
            },
            onDismiss = { categoryDialog = null },
        )
    }

    deleteCategory?.let { target ->
        val count = counts[target.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleteCategory = null },
            title = { Text(stringResource(R.string.favorites_category_delete_title, target.name)) },
            text = { Text(stringResource(R.string.favorites_category_delete_text, count)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCategory = null
                        val keys = favorites
                            .filter { categoryOf[LibraryStore.keyOf(it)] == target.id }
                            .map { LibraryStore.keyOf(it) }
                        FavoriteCategoryStore.delete(context, target.id)
                        if (selectedCategory == target.id) selectedCategory = CATEGORY_ALL
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.favorites_category_deleted),
                                actionLabel = context.getString(R.string.history_undo),
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                FavoriteCategoryStore.restore(context, target, keys)
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
}

@Composable
private fun SelectionBar(
    count: Int,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.resources.getQuantityString(
                    R.plurals.favorites_selected,
                    count,
                    count,
                ),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMove) {
                Text(stringResource(R.string.favorites_selection_move), maxLines = 1)
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.favorites_selection_remove), maxLines = 1)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.favorites_selection_close),
                )
            }
        }
    }
}

/** A category chip that opens its actions on long-press and from the selected chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChip(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) Color.Transparent else colors.outlineVariant,
                ),
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = if (selected) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            maxLines = 1,
        )
        if (selected) {
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_category_options),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
