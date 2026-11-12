package com.kodastream.app.ui.manga

import com.kodastream.app.manga.reader.MangaWebViewActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kodastream.app.R
import com.kodastream.app.manga.ChapterGap
import com.kodastream.app.manga.ChapterProgress
import com.kodastream.app.manga.MangaCategoryStore
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.chapterGaps
import com.kodastream.app.manga.download.MangaDownload
import com.kodastream.app.manga.download.MangaDownloadInfo
import com.kodastream.app.manga.download.downloadKey
import com.kodastream.app.manga.isRecognizedNumber
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.library.CategoryNameDialog
import com.kodastream.app.ui.library.MoveToSheet
import java.text.DateFormat
import java.util.Date

/**
 * Manga details, following Mihon's MangaScreen: a cover/backdrop header with
 * the title, author, artist, status and source; an action row; an expandable
 * description with tags; then the chapter list with a filter/sort header.
 * Wide screens keep the info on the start side and the chapters on the end
 * side, exactly like Mihon's large layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
    ref: MangaRef,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenChapter: (chapter: MangaChapter, page: Int) -> Unit,
    onSearchGenre: (String) -> Unit,
    onOpenDownloads: () -> Unit,
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

    // The in-app browser for a Cloudflare check; coming back refreshes with the new cookie.
    val webViewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }
    val openInAppBrowser: (() -> Unit)? = shareUrl?.let { url ->
        { webViewLauncher.launch(MangaWebViewActivity.intent(context, url, viewModel.sourceId)) }
    }

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
    val unreadCount = remember(state.chapters, state.progress) {
        state.chapters.count { state.progress[it.url]?.read != true }
    }
    val bookmarkCount = remember(state.progress) {
        state.progress.values.count { it.bookmarked }
    }
    val target = remember(state.chapters, state.progress) {
        com.kodastream.app.manga.continueReading(state.chapters, state.progress)
    }
    fun openContinue() {
        viewModel.continueTarget()?.let { next -> onOpenChapter(next.chapter, next.page) }
    }

    val chapterHeader: @Composable (Modifier) -> Unit = { modifier ->
        ChapterHeader(
            state = state,
            shownCount = shownChapters.size,
            scanlators = scanlators,
            chapterState = chapterState,
            modifier = modifier,
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
            onDownload = { viewModel.downloadChapters(listOf(it)) },
            onCancelDownload = { viewModel.cancelDownloads(listOf(it)) },
            onRetryDownload = { viewModel.retryDownloads(listOf(it)) },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        MangaToolbar(
            title = state.entry.title,
            refreshing = state.refreshing,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onShare = ::share,
            onOpenBrowser = ::openBrowser,
            onOpenInApp = openInAppBrowser,
            onMoveToCategory = if (state.favorite) {
                {
                    categories = MangaCategoryStore.categories(context)
                    showMoveSheet = true
                }
            } else {
                null
            },
            onOpenDownloads = onOpenDownloads,
            shareEnabled = shareUrl != null,
            sourceAvailable = state.sourceAvailable,
        )

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
                    if (state.blocked && openInAppBrowser != null) {
                        TextButton(onClick = openInAppBrowser) {
                            Text(stringResource(R.string.manga_reader_open_webview))
                        }
                    }
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        if (state.refreshing && state.chapters.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    val leftState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "hero") {
                            MangaInfoHeader(state.entry, state.entry.sourceName, onSearchGenre)
                        }
                        item(key = "actions") {
                            MangaActionRow(
                                favorite = state.favorite,
                                unreadCount = unreadCount,
                                bookmarkCount = bookmarkCount,
                                unreadOnly = chapterState.unreadOnly,
                                bookmarkedOnly = chapterState.bookmarkedOnly,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onLongPressFavorite = {
                                    if (state.favorite) {
                                        categories = MangaCategoryStore.categories(context)
                                        showMoveSheet = true
                                    }
                                },
                                onToggleUnread = { chapterState.unreadOnly = !chapterState.unreadOnly },
                                onToggleBookmarks = {
                                    chapterState.bookmarkedOnly = !chapterState.bookmarkedOnly
                                },
                                onOpenBrowser = ::openBrowser,
                                browserEnabled = shareUrl != null,
                            )
                        }
                        item(key = "description") {
                            ExpandableDescription(
                                description = state.entry.description,
                                genres = state.entry.genres,
                                onSearchGenre = onSearchGenre,
                            )
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
                        Box(Modifier.weight(1f)) {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 96.dp),
                            ) {
                                chapterRows()
                            }
                            ResumeFab(
                                target = target,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                onClick = ::openContinue,
                            )
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                    ExpressivePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            item(key = "hero") {
                                MangaInfoHeader(state.entry, state.entry.sourceName, onSearchGenre)
                            }
                            item(key = "actions") {
                                MangaActionRow(
                                    favorite = state.favorite,
                                    unreadCount = unreadCount,
                                    bookmarkCount = bookmarkCount,
                                    unreadOnly = chapterState.unreadOnly,
                                    bookmarkedOnly = chapterState.bookmarkedOnly,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onLongPressFavorite = {
                                        if (state.favorite) {
                                            categories = MangaCategoryStore.categories(context)
                                            showMoveSheet = true
                                        }
                                    },
                                    onToggleUnread = {
                                        chapterState.unreadOnly = !chapterState.unreadOnly
                                    },
                                    onToggleBookmarks = {
                                        chapterState.bookmarkedOnly = !chapterState.bookmarkedOnly
                                    },
                                    onOpenBrowser = ::openBrowser,
                                    browserEnabled = shareUrl != null,
                                )
                            }
                            item(key = "description") {
                                ExpandableDescription(
                                    description = state.entry.description,
                                    genres = state.entry.genres,
                                    onSearchGenre = onSearchGenre,
                                )
                            }
                            item(key = "chapters-header") {
                                chapterHeader(Modifier.fillMaxWidth())
                            }
                            chapterRows()
                        }
                    }
                    ResumeFab(
                        target = target,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        onClick = ::openContinue,
                    )
                }
            }
        }

        // Mihon pins the selection actions to the bottom of the screen.
        AnimatedVisibility(
            visible = chapterState.selection.isNotEmpty(),
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            SelectionBar(
                state = state,
                chapterState = chapterState,
                onSetRead = viewModel::setChapterRead,
                onMarkPrevious = viewModel::markPreviousRead,
                onToggleBookmark = viewModel::toggleBookmark,
                onDownload = viewModel::downloadChapters,
                onDeleteDownloads = viewModel::deleteDownloads,
            )
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

/** Mihon's toolbar: back, title that fades in on scroll, filter and overflow. */
@Composable
private fun MangaToolbar(
    title: String,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenInApp: (() -> Unit)?,
    onMoveToCategory: (() -> Unit)?,
    onOpenDownloads: () -> Unit,
    shareEnabled: Boolean,
    sourceAvailable: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        IconButton(onClick = onRefresh, enabled = !refreshing && sourceAvailable) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.manga_refresh))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.manga_reader_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manga_share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    enabled = shareEnabled,
                    onClick = {
                        menu = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manga_open_browser)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    },
                    enabled = shareEnabled,
                    onClick = {
                        menu = false
                        onOpenBrowser()
                    },
                )
                if (onOpenInApp != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_reader_open_webview)) },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenInApp()
                        },
                    )
                }
                if (onMoveToCategory != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_move_to_category)) },
                        leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                        onClick = {
                            menu = false
                            onMoveToCategory()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manga_downloads_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        menu = false
                        onOpenDownloads()
                    },
                )
            }
        }
    }
}

/** Mihon's MangaInfoBox: blurred cover backdrop, cover, title, credits and status. */
@Composable
private fun MangaInfoHeader(
    entry: MangaEntry,
    sourceName: String,
    onSearch: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        if (!entry.thumbnailUrl.isNullOrBlank()) {
            MangaCoverImage(
                sourceId = entry.sourceId,
                url = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.22f)
                    .blur(8.dp),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val copy = LocalContext.current.let { current -> { text: String -> copyToClipboard(current, text) } }

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f),
            ) {
                MangaCoverImage(
                    sourceId = entry.sourceId,
                    url = entry.thumbnailUrl,
                    contentDescription = stringResource(R.string.cd_poster, entry.title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = entry.title.ifBlank { stringResource(R.string.manga_status_unknown) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.combinedClickable(
                        onClick = { if (title.isNotBlank()) onSearch(title) },
                        onLongClick = { if (title.isNotBlank()) copy(title) },
                    ),
                )
                Spacer(Modifier.height(2.dp))
                CreditRow(
                    icon = Icons.Rounded.Person,
                    text = entry.author?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.manga_unknown_author),
                    onClick = { entry.author?.takeIf { it.isNotBlank() }?.let { onSearch(it) } },
                    onLongClick = {
                        entry.author?.takeIf { it.isNotBlank() }?.let { copy(it) }
                    },
                )
                if (!entry.artist.isNullOrBlank() && entry.artist != entry.author) {
                    CreditRow(
                        icon = Icons.Rounded.Brush,
                        text = entry.artist,
                        onClick = { onSearch(entry.artist) },
                        onLongClick = { copy(entry.artist) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(0.75f),
                ) {
                    Icon(
                        imageVector = statusIcon(entry.status),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp),
                    )
                    Text(
                        text = statusLabel(entry.status),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sourceName.isNotBlank()) {
                        Text(
                            text = "  ·  $sourceName",
                            style = MaterialTheme.typography.bodyMedium,
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
private fun CreditRow(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .alpha(0.75f)
            .then(clickable),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun statusLabel(status: Int): String = stringResource(
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

private fun statusIcon(status: Int): ImageVector = when (status) {
    1 -> Icons.Rounded.Schedule
    2 -> Icons.Rounded.CheckCircle
    3 -> Icons.Rounded.AttachMoney
    4 -> Icons.Rounded.Check
    5 -> Icons.Rounded.Close
    6 -> Icons.Rounded.Pause
    else -> Icons.Rounded.Schedule
}

/** Mihon's MangaActionRow: four equal buttons with icon over label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaActionRow(
    favorite: Boolean,
    unreadCount: Int,
    bookmarkCount: Int,
    unreadOnly: Boolean,
    bookmarkedOnly: Boolean,
    onToggleFavorite: () -> Unit,
    onLongPressFavorite: () -> Unit,
    onToggleUnread: () -> Unit,
    onToggleBookmarks: () -> Unit,
    onOpenBrowser: () -> Unit,
    browserEnabled: Boolean,
) {
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        MangaActionButton(
            title = stringResource(
                if (favorite) R.string.manga_in_library else R.string.manga_favorite,
            ),
            icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            color = if (favorite) MaterialTheme.colorScheme.primary else idle,
            onClick = onToggleFavorite,
            onLongClick = onLongPressFavorite,
        )
        MangaActionButton(
            title = stringResource(R.string.manga_action_unread, unreadCount),
            icon = Icons.Rounded.CheckCircle,
            color = if (unreadOnly) MaterialTheme.colorScheme.primary else idle,
            onClick = onToggleUnread,
        )
        MangaActionButton(
            title = stringResource(R.string.manga_action_bookmarks, bookmarkCount),
            icon = Icons.Rounded.BookmarkBorder,
            color = if (bookmarkedOnly) MaterialTheme.colorScheme.primary else idle,
            onClick = onToggleBookmarks,
        )
        MangaActionButton(
            title = stringResource(R.string.manga_web_view),
            icon = Icons.Rounded.Public,
            color = idle,
            onClick = onOpenBrowser,
            enabled = browserEnabled,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MangaActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else color.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = if (enabled) color else color.copy(alpha = 0.38f),
                fontSize = 12.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Mihon's ExpandableMangaDescription: collapsed summary and tags, tap to expand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandableDescription(
    description: String?,
    genres: List<String>,
    onSearchGenre: (String) -> Unit,
) {
    val text = description?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.manga_no_description)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .padding(top = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .combinedClickable(onClick = { expanded = !expanded }),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.show_less else R.string.more,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (genres.isNotEmpty()) {
            if (expanded) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.forEach { genre -> GenreChip(genre, onSearchGenre) }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    genres.take(4).forEach { genre -> GenreChip(genre, onSearchGenre) }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(genre: String, onSearchGenre: (String) -> Unit) {
    AssistChip(
        onClick = { onSearchGenre(genre) },
        label = { Text(genre, style = MaterialTheme.typography.bodySmall) },
    )
}

/** The chapter list header: count, sort and the filter menu. */
@Composable
private fun ChapterHeader(
    state: MangaDetailsState,
    shownCount: Int,
    scanlators: List<String>,
    chapterState: ChapterListState,
    modifier: Modifier,
) {
    var filterMenu by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_chapters_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.manga_chapters_count, shownCount),
                style = MaterialTheme.typography.labelMedium,
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

/** Mihon's bottom action menu for a chapter selection. */
@Composable
private fun SelectionBar(
    state: MangaDetailsState,
    chapterState: ChapterListState,
    onSetRead: (String, Boolean) -> Unit,
    onMarkPrevious: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onDownload: (List<MangaChapter>) -> Unit,
    onDeleteDownloads: (List<MangaChapter>) -> Unit,
) {
    val selection = chapterState.selection
    val selectedChapters = state.chapters.filter { it.url in selection }
    val downloadedSelected = selectedChapters.filter { it.url in state.downloaded }
    val downloadable = selectedChapters.filter { it.url !in state.downloaded }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large.copy(
            bottomStart = ZeroCornerSize,
            bottomEnd = ZeroCornerSize,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.manga_selected_count, selection.size),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            // The actions stay reachable on narrow screens instead of clipping.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloadable.isNotEmpty()) {
                    IconButton(onClick = {
                        onDownload(downloadable)
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.manga_download_start))
                    }
                }
                if (downloadedSelected.isNotEmpty()) {
                    IconButton(onClick = {
                        onDeleteDownloads(downloadedSelected)
                        chapterState.selection = emptySet()
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.manga_download_delete))
                    }
                }
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
}

/** Mihon's Start/Resume FAB, shown only when there is something to read. */
@Composable
private fun ResumeFab(
    target: com.kodastream.app.manga.ContinueReading?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
            text = {
                Text(
                    stringResource(
                        if (target?.allRead == true) R.string.manga_action_start
                        else R.string.manga_action_resume,
                    ),
                )
            },
        )
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
    onDownload: (MangaChapter) -> Unit,
    onCancelDownload: (MangaChapter) -> Unit,
    onRetryDownload: (MangaChapter) -> Unit,
) {
    items(shown, key = { "chapter-${it.url}" }) { chapter ->
        val progress = state.progress[chapter.url]
        val selection = chapterState.selection
        Column {
            ChapterRow(
                chapter = chapter,
                progress = progress,
                downloadInfo = state.queue[downloadKey(state.entry, chapter)],
                downloaded = chapter.url in state.downloaded,
                selected = chapter.url in selection,
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
                onDownload = { onDownload(chapter) },
                onCancelDownload = { onCancelDownload(chapter) },
                onRetryDownload = { onRetryDownload(chapter) },
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
    downloadInfo: MangaDownloadInfo?,
    downloaded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
) {
    val read = progress?.read == true
    val bookmarked = progress?.bookmarked == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!read) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.manga_mark_read),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (bookmarked) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = stringResource(R.string.manga_remove_bookmark),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (read) 0.55f else 1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val subtitle = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (read) 0.55f else 0.8f,
                )
                if (chapter.dateUpload > 0L) {
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(chapter.dateUpload)),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitle,
                    )
                }
                if (progress != null && !read && progress.lastPageRead > 0) {
                    Text(
                        text = stringResource(
                            R.string.manga_chapter_page_progress,
                            progress.lastPageRead + 1,
                            progress.pageCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                when (downloadInfo?.state) {
                    MangaDownload.State.DOWNLOADING -> Text(
                        text = stringResource(R.string.manga_download_percent, downloadInfo.progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MangaDownload.State.QUEUE -> Text(
                        text = stringResource(R.string.manga_download_queued),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    else -> Unit
                }
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(
                    if (bookmarked) R.string.manga_remove_bookmark else R.string.manga_bookmark
                ),
                tint = if (bookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        ChapterDownloadAction(
            downloaded = downloaded,
            info = downloadInfo,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onRetryDownload = onRetryDownload,
        )
    }
}

/** The row's download slot: start, cancel while queued, retry after a failure. */
@Composable
private fun ChapterDownloadAction(
    downloaded: Boolean,
    info: MangaDownloadInfo?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
) {
    when {
        downloaded -> Icon(
            Icons.Rounded.DownloadDone,
            contentDescription = stringResource(R.string.manga_downloads_done),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        info?.state == MangaDownload.State.ERROR -> IconButton(onClick = onRetryDownload) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.manga_download_retry),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        info?.state == MangaDownload.State.DOWNLOADING -> IconButton(onClick = onCancelDownload) {
            Icon(
                Icons.Rounded.Downloading,
                contentDescription = stringResource(R.string.manga_download_cancel),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        info?.state == MangaDownload.State.QUEUE -> IconButton(onClick = onCancelDownload) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = stringResource(R.string.manga_download_cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> IconButton(onClick = onDownload) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = stringResource(R.string.manga_download_start),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissingChaptersDivider(gap: ChapterGap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.manga_missing_chapters, gap.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

private fun MangaChapter.floorNumber(): Float =
    if (isRecognizedNumber()) kotlin.math.floor(number) else -1f

private fun copyToClipboard(context: android.content.Context, text: String) {
    runCatching {
        val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("manga", text))
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
