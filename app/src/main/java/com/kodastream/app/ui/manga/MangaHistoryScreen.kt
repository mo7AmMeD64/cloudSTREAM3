package com.kodastream.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.posterMinWidth
import com.kodastream.app.manga.MangaHistoryEntry
import com.kodastream.app.manga.MangaHistoryStore
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.PosterSizeButton
import com.kodastream.app.ui.components.SearchField
import com.kodastream.app.ui.components.SectionHeader
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Manga history: one row per title, grouped by the day it was last read. */
@Composable
fun MangaHistoryScreen(
    contentPadding: PaddingValues,
    onOpenDetails: (MangaRef) -> Unit,
    onResume: (MangaRef, chapterUrl: String, page: Int) -> Unit,
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(MangaHistoryStore.entries(context)) }
    var query by remember { mutableStateOf("") }
    var posterStep by remember { mutableIntStateOf(MangaPrefs.historyPosterStep(context)) }
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        MangaHistoryStore.revision.collect {
            entries = MangaHistoryStore.entries(context)
        }
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) {
            entries
        } else {
            entries.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.chapterName.contains(query, ignoreCase = true) ||
                    it.chapterNumber.toString().contains(query)
            }
        }
    }

    fun ref(entry: MangaHistoryEntry) = MangaRef(
        sourceId = entry.sourceId,
        url = entry.mangaUrl,
        title = entry.title,
        thumbnailUrl = entry.thumbnailUrl,
    )

    fun remove(entry: MangaHistoryEntry) {
        MangaHistoryStore.remove(context, entry)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.history_removed),
                actionLabel = context.getString(R.string.history_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                MangaHistoryStore.restore(context, listOf(entry))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding() + 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_history),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    PosterSizeButton(
                        step = posterStep,
                        onStepChange = { step ->
                            posterStep = step
                            MangaPrefs.setHistoryPosterStep(context, step)
                        },
                    )
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.cd_history_clear),
                        )
                    }
                }
            }

            if (entries.size > 8) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.manga_history_search_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                entries.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.manga_history_empty_title),
                    hint = stringResource(R.string.manga_history_empty_hint),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                }
                filtered.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.manga_library_no_results_title),
                    hint = stringResource(R.string.manga_library_no_results_hint),
                    modifier = Modifier.weight(1f),
                )
                else -> MangaHistoryList(
                    entries = filtered,
                    posterStep = posterStep,
                    contentPadding = contentPadding,
                    onOpenDetails = { onOpenDetails(ref(it)) },
                    onResume = { onResume(ref(it), it.chapterUrl, it.page) },
                    onRemove = ::remove,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    MangaHistoryStore.clear(context)
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.history_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MangaHistoryList(
    entries: List<MangaHistoryEntry>,
    posterStep: Int,
    contentPadding: PaddingValues,
    onOpenDetails: (MangaHistoryEntry) -> Unit,
    onResume: (MangaHistoryEntry) -> Unit,
    onRemove: (MangaHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val yesterday = remember { today.minusDays(1) }
    val groups = remember(entries) {
        entries.groupBy { entry ->
            val date = Instant.ofEpochMilli(entry.readAt).atZone(zone).toLocalDate()
            when (date) {
                today -> 0
                yesterday -> 1
                else -> date
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (group, groupEntries) ->
            item(key = "header-$group") {
                SectionHeader(dayLabel(group, zone))
            }
            items(groupEntries, key = { "${it.sourceId}|${it.mangaUrl}" }) { entry ->
                SwipeableHistoryRow(
                    entry = entry,
                    posterStep = posterStep,
                    onClick = { onOpenDetails(entry) },
                    onResume = { onResume(entry) },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryRow(
    entry: MangaHistoryEntry,
    posterStep: Int,
    onClick: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onRemove()
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_history),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
    ) {
        MangaHistoryRow(
            entry = entry,
            posterStep = posterStep,
            onClick = onClick,
            onResume = onResume,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun MangaHistoryRow(
    entry: MangaHistoryEntry,
    posterStep: Int,
    onClick: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val coverWidth = posterMinWidth(posterStep)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCoverImage(
                sourceId = entry.sourceId,
                url = entry.thumbnailUrl,
                contentDescription = entry.title,
                modifier = Modifier
                    .width(coverWidth)
                    .aspectRatio(2f / 3f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = chapterLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = android.text.format.DateFormat.getTimeFormat(context).format(Date(entry.readAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onResume,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.resume))
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_history),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun dayLabel(group: Any, zone: ZoneId): String = when (group) {
    0 -> stringResource(R.string.history_today)
    1 -> stringResource(R.string.history_yesterday)
    else -> {
        val date = group as LocalDate
        DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date(date.atStartOfDay(zone).toInstant().toEpochMilli()))
    }
}

@Composable
private fun chapterLabel(entry: MangaHistoryEntry): String {
    val number = entry.chapterNumber
    return if (number >= 0f) {
        stringResource(R.string.manga_chapter_number, formatNumber(number))
    } else {
        entry.chapterName.ifBlank { stringResource(R.string.manga_chapter_number, "?") }
    }
}

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
