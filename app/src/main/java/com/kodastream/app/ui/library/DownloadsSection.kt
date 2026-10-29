package com.kodastream.app.ui.library

import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.DownloadStore.Download
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SectionHeader
import com.kodastream.app.ui.components.episodeBadge
import com.kodastream.app.ui.components.episodeBadgeDescription
import com.kodastream.app.ui.components.episodeBadgeLabel

/**
 * The Library's Downloads tab: every downloaded episode and movie, series
 * grouped under their title, with live progress straight from the store.
 * Playing a finished item never touches the network - the player gets the
 * local file URI, and history records it like any other playback.
 */
@Composable
fun DownloadsSection(
    contentPadding: PaddingValues,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
) {
    val context = LocalContext.current
    var downloads by remember { mutableStateOf(DownloadStore.current(context)) }
    LaunchedEffect(Unit) {
        DownloadStore.entries.collect { updated -> updated?.let { downloads = it } }
    }

    val snackbar = remember { SnackbarHostState() }

    // A picked folder whose permission slipped away falls back to the default
    // (validTree clears the pref itself); the user should know that happened.
    LaunchedEffect(Unit) {
        if (AppPrefs.downloadTreeUri(context) != null && DownloadLocations.validTree(context) == null) {
            snackbar.showSnackbar(context.getString(R.string.dl_permission_lost))
        }
    }

    var pendingDelete by remember { mutableStateOf<Download?>(null) }
    var pendingDeleteTitle by remember { mutableStateOf<List<Download>?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.nav_downloads),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (downloads.isNotEmpty()) {
                    IconButton(onClick = { showClearAll = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.downloads_delete_all),
                        )
                    }
                }
            }

            if (downloads.isEmpty()) {
                EmptyBox(
                    title = stringResource(R.string.downloads_empty_title),
                    hint = stringResource(R.string.downloads_empty_hint),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                DownloadsList(
                    downloads = downloads,
                    contentPadding = contentPadding,
                    onOpenItem = onOpenItem,
                    onResume = onResume,
                    onDelete = { pendingDelete = it },
                    onDeleteTitle = { pendingDeleteTitle = it },
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

    pendingDelete?.let { download ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.downloads_delete_confirm_title)) },
            text = { Text(download.title) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadLocations.delete(context, download.fileUri)
                    // Its subtitle goes with the episode; the poster is
                    // internal and follows too.
                    DownloadLocations.delete(context, download.subtitleUri)
                    DownloadLocations.delete(context, download.posterUri)
                    DownloadStore.remove(context, download.id)
                    // The title folder goes when its last file does.
                    DownloadLocations.deleteFolderIfEmpty(context, download.folderUri)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.downloads_delete_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingDeleteTitle?.let { titleRecords ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTitle = null },
            title = { Text(stringResource(R.string.downloads_delete_title_confirm)) },
            text = { Text(titleRecords.first().title) },
            confirmButton = {
                TextButton(onClick = {
                    titleRecords.forEach { download ->
                        DownloadLocations.delete(context, download.fileUri)
                        DownloadLocations.delete(context, download.posterUri)
                        DownloadStore.remove(context, download.id)
                    }
                    DownloadLocations.deleteTitleFolder(
                        context,
                        titleRecords.first().folderUri,
                        titleRecords.flatMap { listOfNotNull(it.fileUri, it.subtitleUri) }.toSet(),
                    )
                    pendingDeleteTitle = null
                }) {
                    Text(stringResource(R.string.downloads_delete_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTitle = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text(stringResource(R.string.downloads_delete_all)) },
            text = { Text(stringResource(R.string.downloads_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val records = DownloadStore.current(context)
                    records.forEach { download ->
                        DownloadLocations.delete(context, download.fileUri)
                        DownloadLocations.delete(context, download.posterUri)
                    }
                    // Only folders the app's own records point at - never
                    // anything the app did not create.
                    records.mapNotNull { it.folderUri }.distinct().forEach { folder ->
                        DownloadLocations.deleteTitleFolder(
                            context, folder,
                            records.flatMap { listOfNotNull(it.fileUri, it.subtitleUri) }.toSet(),
                        )
                    }
                    DownloadStore.clear(context)
                    showClearAll = false
                }) {
                    Text(stringResource(R.string.downloads_delete_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DownloadsList(
    downloads: List<Download>,
    contentPadding: PaddingValues,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
    onDelete: (Download) -> Unit,
    onDeleteTitle: (List<Download>) -> Unit,
) {
    val context = LocalContext.current
    // One group per title - series episodes together, each movie on its own.
    val groups = remember(downloads) {
        downloads
            .groupBy { "${it.apiName}|${it.url}" }
            .map { (_, list) -> list.sortedByDescending { it.createdAt } }
            .sortedByDescending { list -> list.maxOf { it.createdAt } }
    }
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEach { groupItems ->
            val group = groupItems.first()
            val groupKey = "${group.apiName}|${group.url}"
            val expanded = groupKey !in collapsed
            item(key = "title-$groupKey") {
                TitleCard(
                    title = group.title,
                    posterUrl = group.posterUri ?: group.posterUrl,
                    posterHeaders = group.posterHeaders,
                    episodeCount = groupItems.size,
                    totalBytes = groupItems.sumOf { if (it.totalBytes > 0) it.totalBytes else it.receivedBytes },
                    expanded = expanded,
                    onToggle = {
                        collapsed = if (expanded) collapsed + groupKey else collapsed - groupKey
                    },
                    onDelete = { onDeleteTitle(groupItems) },
                )
            }
            if (expanded) {
                val items = groupItems
                items(items, key = { it.id }) { download ->
                DownloadRow(
                    download = download,
                    onPlay = {
                        val uri = download.fileUri
                        if (uri != null && DownloadLocations.exists(context, uri)) {
                            val offlineSub = download.subtitleUri
                                ?.takeIf { DownloadLocations.exists(context, it) }
                            onResume(
                                PlayerArgs(
                                    apiName = download.apiName,
                                    data = uri,
                                    title = download.title,
                                    subtitle = download.episodeTitle,
                                    resumeKey = "local|${download.id}",
                                    offlineSubtitleUri = offlineSub,
                                    history = HistoryEntry(
                                        key = "${download.apiName}|${download.url}",
                                        apiName = download.apiName,
                                        url = download.url,
                                        title = download.title,
                                        posterUrl = download.posterUrl,
                                        posterHeaders = download.posterHeaders,
                                        type = download.type,
                                        season = download.season,
                                        episode = download.episode,
                                        episodeTitle = download.episodeTitle,
                                        data = uri,
                                        subtitle = null,
                                        resumeKey = "local|${download.id}",
                                    ),
                                ),
                            )
                        } else {
                            // The file vanished (folder removed, sd-card gone):
                            // fall back to the details page, which explains.
                            onOpenItem(
                                DetailsArgs(
                                    apiName = download.apiName,
                                    url = download.url,
                                    title = download.title,
                                    posterUrl = download.posterUrl,
                                    posterHeaders = download.posterHeaders,
                                ),
                            )
                        }
                    },
                    onState = { action ->
                        when (action) {
                            DownloadAction.PAUSE -> DownloadService.pause(context, download.id)
                            DownloadAction.RESUME -> DownloadService.resume(context, download.id)
                            DownloadAction.RETRY -> DownloadService.retry(context, download.id)
                            DownloadAction.CANCEL -> DownloadService.cancel(context, download.id)
                        }
                    },
                    onDelete = { onDelete(download) },
                )
                }
            }
        }
    }
}

/**
 * One title: its poster, how many episodes are here, how much space they
 * take, and the actions on the whole folder - expand to see the episodes.
 */
@Composable
private fun TitleCard(
    title: String,
    posterUrl: String?,
    posterHeaders: Map<String, String>?,
    episodeCount: Int,
    totalBytes: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteImage(
                url = posterUrl,
                headers = posterHeaders,
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = Formatter.formatFileSize(context, totalBytes)
                Text(
                    text = stringResource(R.string.downloads_episodes_count, episodeCount) + "  •  " + size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.downloads_delete_title),
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse else R.string.cd_expand,
                    ),
                )
            }
        }
    }
}

private enum class DownloadAction { PAUSE, RESUME, RETRY, CANCEL }

/** One downloaded item: poster, what it is, its size, its state, and its actions. */
@Composable
private fun DownloadRow(
    download: Download,
    onPlay: () -> Unit,
    onState: (DownloadAction) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val active = download.state == DownloadStore.STATE_DOWNLOADING ||
        download.state == DownloadStore.STATE_QUEUED

    val badge = episodeBadge(download.season, download.episode)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RemoteImage(
                    url = download.posterUri ?: download.posterUrl,
                    headers = download.posterHeaders,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.small),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (badge != null) {
                        val episodeLabel = episodeBadgeLabel(badge)
                        val episodeDescription = episodeBadgeDescription(badge)
                        Text(
                            text = episodeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = episodeDescription
                            },
                        )
                    }
                    Text(
                        text = download.episodeTitle?.takeIf { it.isNotBlank() } ?: download.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val size = if (download.totalBytes > 0) {
                        Formatter.formatFileSize(context, download.totalBytes)
                    } else {
                        Formatter.formatFileSize(context, download.receivedBytes)
                    }
                    Text(
                        text = size + "  •  " + stateLabel(download),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (download.state) {
                    DownloadStore.STATE_DONE -> {
                        IconButton(onClick = onPlay) {
                            Icon(Icons.Rounded.PlayCircle, stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.downloads_delete_file))
                        }
                    }

                    DownloadStore.STATE_DOWNLOADING -> {
                        IconButton(onClick = { onState(DownloadAction.PAUSE) }) {
                            Icon(Icons.Rounded.PauseCircle, stringResource(R.string.cd_download_pause))
                        }
                        IconButton(onClick = { onState(DownloadAction.CANCEL) }) {
                            Icon(Icons.Rounded.Cancel, stringResource(R.string.cd_download_cancel))
                        }
                    }

                    DownloadStore.STATE_PAUSED -> {
                        IconButton(onClick = { onState(DownloadAction.RESUME) }) {
                            Icon(Icons.Rounded.PlayCircle, stringResource(R.string.cd_download_resume), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onState(DownloadAction.CANCEL) }) {
                            Icon(Icons.Rounded.Cancel, stringResource(R.string.cd_download_cancel))
                        }
                    }

                    DownloadStore.STATE_QUEUED -> {
                        IconButton(onClick = { onState(DownloadAction.CANCEL) }) {
                            Icon(Icons.Rounded.Cancel, stringResource(R.string.cd_download_cancel))
                        }
                    }

                    DownloadStore.STATE_FAILED -> {
                        IconButton(onClick = { onState(DownloadAction.RETRY) }) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.cd_download_retry), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.downloads_delete_file))
                        }
                    }
                }
            }

            if (active) {
                val fraction = download.progressFraction
                if (fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(3.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .height(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun stateLabel(download: Download): String = when (download.state) {
    DownloadStore.STATE_DONE -> stringResource(R.string.download_done)
    DownloadStore.STATE_DOWNLOADING -> {
        val fraction = download.progressFraction
        if (fraction >= 0f) stringResource(R.string.download_downloading, (fraction * 100).toInt())
        else stringResource(R.string.download_downloading, 0)
    }
    DownloadStore.STATE_PAUSED -> stringResource(R.string.download_paused)
    DownloadStore.STATE_QUEUED -> stringResource(R.string.download_queued)
    DownloadStore.STATE_FAILED -> stringResource(R.string.download_failed) +
        (download.error?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
    else -> stringResource(R.string.download_failed)
}
