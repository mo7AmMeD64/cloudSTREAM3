package com.kodastream.app.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.data.posterMinWidth
import com.kodastream.app.engine.Engine
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.PosterSizeButton
import com.kodastream.app.ui.components.PosterTitle
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.bouncySpring
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The three age groups a history entry can land in. */
private val GROUP_LABELS = listOf(
    R.string.history_today,
    R.string.history_yesterday,
    R.string.history_earlier,
)

@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(HistoryStore.current(context)) }
    LaunchedEffect(Unit) {
        HistoryStore.entries.collect { updated -> updated?.let { history = it } }
    }
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { updated -> updated?.let { watchedKeys = it } }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var posterStep by remember { mutableIntStateOf(AppPrefs.posterSizeStep(context)) }

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
                if (history.isNotEmpty()) {
                    PosterSizeButton(
                        step = posterStep,
                        onStepChange = { step ->
                            posterStep = step
                            AppPrefs.setPosterSizeStep(context, step)
                        },
                    )
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.cd_history_clear),
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                EmptyBox(
                    title = stringResource(R.string.history_empty_title),
                    hint = stringResource(R.string.history_empty_hint),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                HistoryGrid(
                    history = history,
                    watchedKeys = watchedKeys,
                    posterStep = posterStep,
                    contentPadding = contentPadding,
                    onOpenItem = onOpenItem,
                    onResume = onResume,
                    onRemove = { entry ->
                        HistoryStore.remove(context, entry.key)
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.history_removed),
                                actionLabel = context.getString(R.string.history_undo),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                HistoryStore.upsert(context, entry)
                            }
                        }
                    },
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
                    HistoryStore.clear(context)
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
private fun HistoryGrid(
    history: List<HistoryEntry>,
    watchedKeys: Set<String>,
    posterStep: Int,
    contentPadding: PaddingValues,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    // Today / Yesterday / Earlier, newest first within each group.
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val yesterday = remember { today.minusDays(1) }
    fun groupOf(entry: HistoryEntry): Int {
        val date = Instant.ofEpochMilli(entry.lastWatchedAt).atZone(zone).toLocalDate()
        return when {
            date == today -> 0
            date == yesterday -> 1
            else -> 2
        }
    }

    val groups = remember(history) {
        GROUP_LABELS.mapIndexed { index, _ -> index to history.filter { groupOf(it) == index } }
            .filter { (_, entries) -> entries.isNotEmpty() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = posterMinWidth(posterStep)),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        groups.forEach { (group, entries) ->
            item(key = "header-$group", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(GROUP_LABELS[group]))
            }
            items(entries, key = { it.key }, span = { GridItemSpan(1) }) { entry ->
                HistoryCard(
                    entry = entry,
                    watched = WatchedStore.keyOf(entry.apiName, entry.url, entry.season, entry.episode) in watchedKeys,
                    onClick = {
                        if (Engine.provider(entry.apiName) == null) {
                            // The provider is gone: fall back to the details
                            // page, which explains the problem instead of
                            // crashing or playing a dead link.
                            onOpenItem(
                                DetailsArgs(
                                    apiName = entry.apiName,
                                    url = entry.url,
                                    title = entry.title,
                                    posterUrl = entry.posterUrl,
                                    posterHeaders = entry.posterHeaders,
                                )
                            )
                        } else {
                            onResume(
                                PlayerArgs(
                                    apiName = entry.apiName,
                                    data = entry.data,
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    resumeKey = entry.resumeKey,
                                    history = entry,
                                )
                            )
                        }
                    },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

/**
 * One watched title: the same card language as the Home shelves - tonal
 * surface, poster with scrim badges, title below - plus a thin progress bar
 * where watching stopped (hidden once 90% is done) and an "S2 E5" pill for
 * episodes. Long-press or the corner X removes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    watched: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = bouncySpring(),
        label = "historyPress",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onRemove,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column {
            Box {
                RemoteImage(
                    url = entry.posterUrl,
                    headers = entry.posterHeaders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )

                // The episode label sits on a photograph, so a scrim keeps it
                // readable in every palette - the same reasoning as the score
                // badge on posters.
                if (entry.season != null || entry.episode != null) {
                    Text(
                        text = "S${entry.season ?: 1} E${entry.episode ?: 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Surface(
                    onClick = onRemove,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_history),
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (watched) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.cd_watched),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                            .padding(4.dp)
                            .size(14.dp),
                    )
                }
                if (!entry.isComplete) {
                    LinearProgressIndicator(
                        progress = {
                            if (entry.durationMs > 0) {
                                (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.White,
                        trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                    )
                }
            }
            PosterTitle(text = entry.title)
        }
    }
}
