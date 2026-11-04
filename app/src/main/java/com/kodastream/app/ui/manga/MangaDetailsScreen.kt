package com.kodastream.app.ui.manga

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kodastream.app.R
import com.kodastream.app.data.FavoriteCategory
import com.kodastream.app.manga.ChapterGap
import com.kodastream.app.manga.ChapterProgress
import com.kodastream.app.manga.MangaCategoryStore
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.chapterGaps
import com.kodastream.app.manga.isLastPage
import com.kodastream.app.manga.isRecognizedNumber
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.library.MoveToSheet
import com.kodastream.app.ui.library.CategoryNameDialog
import java.text.DateFormat
import java.util.Date

/**
 * Manga details: cached record first, then a refresh through the source's
 * single getMangaUpdate entry point. Wide screens put the hero on the start
 * side and the chapter list on the end side; the chapter list owns its own
 * scroll there, so a long list never pushes the actions away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
    ref: MangaRef,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenChapter: (chapter: MangaChapter, page: Int) -> Unit,
    onSearchGenre: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: MangaDetailsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MangaDetailsViewModel(app, ref) }
        },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showMoveSheet by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf(MangaCategoryStore.categories(context)) }

    val shareUrl = remember(state.entry, state.favorite) { viewModel.shareUrl() }

    fun openBrowser() {
        val url = shareUrl ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun share() {
        val url = shareUrl ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$url\n${state.entry.title}")
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = state.entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::share, enabled = shareUrl != null) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.manga_share))
            }
            IconButton(onClick = ::openBrowser, enabled = shareUrl != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.manga_open_browser),
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.manga_refresh))
            }
        }

        if (!state.sourceAvailable) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.manga_source_missing),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        if (state.refreshing && state.chapters.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val chapterState = rememberSaveable(saver = ChapterListState.Saver) { ChapterListState() }
        val scanlators = remember(state.chapters) {
            state.chapters.mapNotNull { it.scanlator?.takeIf(String::isNotBlank) }.distinct().sorted()
        }
        val shownChapters = remember(
            state.chapters,
            state.progress,
            chapterState.ascending,
            chapterState.unreadOnly,
            chapterState.bookmarkedOnly,
            chapterState.scanlator,
        ) {
            val filtered = state.chapters.filter { chapter ->
                val progress = state.progress[chapter.url]
                (!chapterState.unreadOnly || progress?.read != true) &&
                    (!chapterState.bookmarkedOnly || progress?.bookmarked == true) &&
                    (chapterState.scanlator == null || chapter.scanlator == chapterState.scanlator)
            }
            val ordered = filtered.sortedBy { it.sourceOrder }
            if (chapterState.ascending) ordered else ordered.reversed()
        }
        val gaps = remember(state.chapters) {
            chapterGaps(state.chapters).associateBy { it.afterNumber }
        }
        val chapterHeader = @Composable { modifier: Modifier ->
            ChapterHeader(
                state = state,
                shownCount = shownChapters.size,
                scanlators = scanlators,
                chapterState = chapterState,
                modifier = modifier,
                onSetRead = viewModel::setChapterRead,
                onMarkPrevious = viewModel::markPreviousRead,
                onToggleBookmark = viewModel::toggleBookmark,
            )
        }
        val chapterRows: LazyListScope.() -> Unit = {
            chapterItems(
                state = state,
                shown = shownChapters,
                gaps = gaps,
                chapterState = chapterState,
                onOpenChapter = onOpenChapter,
                onToggleBookmark = viewModel::toggleBookmark,
            )
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "hero") {
                            DetailsHero(
                                entry = state.entry,
                                sourceName = state.entry.sourceName,
                                onSearchGenre = onSearchGenre,
                            )
                        }
                        item(key = "actions") {
                            PrimaryActions(
                                state = state,
                                onContinue = {
                                    viewModel.continueTarget()?.let { target ->
                                        onOpenChapter(target.chapter, target.page)
                                    }
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onLongPressFavorite = {
                                    if (state.favorite) {
                                        categories = MangaCategoryStore.categories(context)
                                        showMoveSheet = true
                                    }
                                },
                            )
                        }
                        item(key = "overview") {
                            Overview(state.entry.description)
                        }
                        item(key = "genres") {
                            GenreChips(state.entry.genres, onSearchGenre)
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                    ) {
                        chapterHeader(Modifier.fillMaxWidth())
                        LazyColumn(state = rememberLazyListState()) {
                            chapterRows()
                            item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "hero") {
                        DetailsHero(
                            entry = state.entry,
                            sourceName = state.entry.sourceName,
                            onSearchGenre = onSearchGenre,
                        )
                    }
                    item(key = "actions") {
                        PrimaryActions(
                            state = state,
                            onContinue = {
                                viewModel.continueTarget()?.let { target ->
                                    onOpenChapter(target.chapter, target.page)
                                }
                            },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onLongPressFavorite = {
                                if (state.favorite) {
                                    categories = MangaCategoryStore.categories(context)
                                    showMoveSheet = true
                                }
                            },
                        )
                    }
                    item(key = "overview") {
                        Overview(state.entry.description)
                    }
                    item(key = "genres") {
                        GenreChips(state.entry.genres, onSearchGenre)
                    }
                    item(key = "chapters-header") {
                        chapterHeader(Modifier.fillMaxWidth())
                    }
                    chapterRows()
                    item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showMoveSheet) {
        MoveToSheet(
            categories = categories,
            selectedCategoryIds = setOf(state.categoryId),
            onDismiss = { showMoveSheet = false },
            onMove = { id ->
                viewModel.moveToCategory(id)
                showMoveSheet = false
            },
            onCreateAndMove = {
                showMoveSheet = false
                showCategoryDialog = true
            },
        )
    }
    if (showCategoryDialog) {
        CategoryNameDialog(
            title = stringResource(R.string.favorites_category_new),
            initialName = "",
            confirmLabel = stringResource(R.string.favorites_category_create),
            categories = categories,
            editingId = null,
            onConfirm = { name ->
                val result = MangaCategoryStore.create(context, name)
                if (result is com.kodastream.app.data.CategoryResult.Created) {
                    categories = MangaCategoryStore.categories(context)
                    viewModel.moveToCategory(result.category.id)
                }
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false },
        )
    }
}

@Composable
private fun DetailsHero(
    entry: MangaEntry,
    sourceName: String,
    onSearchGenre: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
    ) {
        MangaCoverImage(
            sourceId = entry.sourceId,
            url = entry.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        0.75f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.background,
                    )
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                shadowElevation = 12.dp,
                modifier = Modifier.size(112.dp, 166.dp),
            ) {
                MangaCoverImage(
                    sourceId = entry.sourceId,
                    url = entry.thumbnailUrl,
                    contentDescription = stringResource(R.string.cd_poster, entry.title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val authors = listOfNotNull(
                    entry.author?.takeIf { it.isNotBlank() },
                    entry.artist?.takeIf { it.isNotBlank() && it != entry.author },
                )
                if (authors.isNotEmpty()) {
                    Text(
                        text = authors.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(entry.status)
                    if (sourceName.isNotBlank()) {
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: Int) {
    val label = stringResource(
        when (status) {
            1 -> R.string.manga_status_ongoing
            2 -> R.string.manga_status_completed
            3 -> R.string.manga_status_licensed
            4 -> R.string.manga_status_publishing_finished
            5 -> R.string.manga_status_cancelled
            6 -> R.string.manga_status_on_hiatus
            else -> R.string.manga_status_unknown
        }
    )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrimaryActions(
    state: MangaDetailsState,
    onContinue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPressFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val target = remember(state.chapters, state.progress) {
            com.kodastream.app.manga.continueReading(state.chapters, state.progress)
        }
        Button(
            onClick = onContinue,
            enabled = target != null,
            modifier = Modifier.weight(1f),
        ) {
            val label = when {
                target == null -> stringResource(R.string.manga_no_chapters)
                target.allRead -> stringResource(R.string.manga_start_reading)
                target.page > 0 -> stringResource(R.string.manga_continue_page, target.page + 1)
                target.chapter.isRecognizedNumber() -> stringResource(
                    R.string.manga_continue_reading,
                    formatNumber(target.chapter.number),
                )
                else -> stringResource(R.string.manga_continue_reading, target.chapter.name)
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    onClick = onToggleFavorite,
                    onLongClick = onLongPressFavorite,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(
                    if (state.favorite) R.string.manga_unfavorite else R.string.manga_favorite
                ),
                tint = if (state.favorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Overview(description: String?) {
    if (description.isNullOrBlank()) return
    var expanded by rememberSaveable(description) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = description,
            // Content direction, so an Arabic summary reads right-to-left
            // inside an English UI and the reverse.
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.show_less else R.string.more
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(genres: List<String>, onSearchGenre: (String) -> Unit) {
    if (genres.isEmpty()) return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.manga_genres),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { genre ->
                AssistChip(
                    onClick = { onSearchGenre(genre) },
                    label = { Text(genre) },
                )
            }
        }
    }
}

/** Sort, filter and selection for the chapter list, kept across rotation. */
private class ChapterListState(
    ascending: Boolean = true,
    unreadOnly: Boolean = false,
    bookmarkedOnly: Boolean = false,
    scanlator: String? = null,
    selection: Set<String> = emptySet(),
) {
    var ascending by mutableStateOf(ascending)
    var unreadOnly by mutableStateOf(unreadOnly)
    var bookmarkedOnly by mutableStateOf(bookmarkedOnly)
    var scanlator by mutableStateOf(scanlator)
    var selection by mutableStateOf(selection)

    companion object {
        val Saver = listSaver<ChapterListState, Any?>(
            save = {
                listOf(
                    it.ascending,
                    it.unreadOnly,
                    it.bookmarkedOnly,
                    it.scanlator,
                    ArrayList(it.selection),
                )
            },
            restore = {
                ChapterListState(
                    ascending = it[0] as Boolean,
                    unreadOnly = it[1] as Boolean,
                    bookmarkedOnly = it[2] as Boolean,
                    scanlator = it[3] as String?,
                    selection = (it[4] as List<*>).filterIsInstance<String>().toSet(),
                )
            },
        )
    }
}

@Composable
private fun ChapterHeader(
    state: MangaDetailsState,
    shownCount: Int,
    scanlators: List<String>,
    chapterState: ChapterListState,
    modifier: Modifier,
    onSetRead: (String, Boolean) -> Unit,
    onMarkPrevious: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
) {
    var filterMenu by remember { mutableStateOf(false) }
    val selection = chapterState.selection

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_chapters_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.manga_chapters_count, shownCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { chapterState.ascending = !chapterState.ascending }) {
                Icon(
                    Icons.Rounded.Sort,
                    contentDescription = stringResource(
                        if (chapterState.ascending) R.string.manga_sort_ascending
                        else R.string.manga_sort_descending
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { filterMenu = true }) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.manga_filters),
                        tint = if (
                            chapterState.unreadOnly ||
                            chapterState.bookmarkedOnly ||
                            chapterState.scanlator != null
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_chapter_filter_unread)) },
                        trailingIcon = if (chapterState.unreadOnly) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = { chapterState.unreadOnly = !chapterState.unreadOnly },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_chapter_filter_bookmarked)) },
                        trailingIcon = if (chapterState.bookmarkedOnly) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = { chapterState.bookmarkedOnly = !chapterState.bookmarkedOnly },
                    )
                    if (scanlators.size > 1) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manga_chapter_scanlator_all)) },
                            trailingIcon = if (chapterState.scanlator == null) {
                                { Icon(Icons.Rounded.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = { chapterState.scanlator = null },
                        )
                        scanlators.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                trailingIcon = if (chapterState.scanlator == name) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                onClick = { chapterState.scanlator = name },
                            )
                        }
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.manga_selected_count, selection.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        selection.forEach { onSetRead(it, true) }
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.manga_mark_read))
                    }
                    IconButton(onClick = {
                        selection.forEach { onSetRead(it, false) }
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.manga_mark_unread))
                    }
                    IconButton(onClick = {
                        state.chapters.lastOrNull { it.url in selection }?.url?.let(onMarkPrevious)
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.manga_mark_previous))
                    }
                    IconButton(onClick = {
                        selection.forEach(onToggleBookmark)
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.BookmarkBorder, contentDescription = stringResource(R.string.manga_bookmark))
                    }
                }
            }
        }

        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.chapters.isEmpty() && !state.loading && state.error == null) {
            Text(
                text = stringResource(R.string.manga_no_chapters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/** Chapter rows for whichever lazy list is hosting them: the page or the wide pane. */
private fun LazyListScope.chapterItems(
    state: MangaDetailsState,
    shown: List<MangaChapter>,
    gaps: Map<Float, ChapterGap>,
    chapterState: ChapterListState,
    onOpenChapter: (chapter: MangaChapter, page: Int) -> Unit,
    onToggleBookmark: (String) -> Unit,
) {
    items(shown, key = { "chapter-${it.url}" }) { chapter ->
        val progress = state.progress[chapter.url]
        val selection = chapterState.selection
        Column {
            ChapterRow(
                chapter = chapter,
                progress = progress,
                selected = chapter.url in selection,
                selectionMode = selection.isNotEmpty(),
                onClick = {
                    if (selection.isNotEmpty()) {
                        chapterState.selection = selection.toggle(chapter.url)
                    } else {
                        val page = if (progress?.read == true) 0 else progress?.lastPageRead ?: 0
                        onOpenChapter(chapter, page)
                    }
                },
                onLongClick = {
                    chapterState.selection = selection.toggle(chapter.url)
                },
                onToggleBookmark = { onToggleBookmark(chapter.url) },
            )
            gaps[chapter.floorNumber()]?.let { gap ->
                MissingChaptersDivider(gap)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: MangaChapter,
    progress: ChapterProgress?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val read = progress?.read == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (read) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chapter.dateUpload > 0L) {
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(chapter.dateUpload)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!read && (progress?.lastPageRead ?: 0) > 0) {
                    Text(
                        text = stringResource(
                            R.string.manga_chapter_page_progress,
                            (progress?.lastPageRead ?: 0) + 1,
                            progress?.pageCount ?: 0,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (read && !selectionMode) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.manga_mark_read),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (progress?.bookmarked == true) {
                    Icons.Rounded.Bookmark
                } else {
                    Icons.Rounded.BookmarkBorder
                },
                contentDescription = stringResource(
                    if (progress?.bookmarked == true) R.string.manga_remove_bookmark
                    else R.string.manga_bookmark
                ),
                tint = if (progress?.bookmarked == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MissingChaptersDivider(gap: ChapterGap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.manga_missing_chapters, gap.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

private fun MangaChapter.floorNumber(): Float =
    if (isRecognizedNumber()) kotlin.math.floor(number) else -1f

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
