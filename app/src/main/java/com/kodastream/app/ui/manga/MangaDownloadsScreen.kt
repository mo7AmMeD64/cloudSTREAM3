package com.kodastream.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kodastream.app.R
import com.kodastream.app.manga.download.MangaDownload
import com.kodastream.app.manga.download.MangaDownloadInfo
import com.kodastream.app.manga.download.MangaDownloadManager
import kotlinx.coroutines.launch

/** Mihon's download queue: what is running, what waits, and what failed. */
@Composable
fun MangaDownloadsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { MangaDownloadManager.get(context) }
    val scope = rememberCoroutineScope()
    val queue by manager.queue.collectAsState()
    val queueInfo by manager.queueInfo.collectAsState()
    val running by manager.isRunning.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = stringResource(R.string.manga_downloads_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .semantics { heading() },
            )
            if (queue.isNotEmpty()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            if (running) manager.pause() else manager.start()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (running) R.string.manga_downloads_pause else R.string.manga_downloads_resume,
                        ),
                    )
                }
                IconButton(onClick = { scope.launch { manager.clearQueue() } }) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.manga_downloads_clear),
                    )
                }
            }
        }
        HorizontalDivider()

        if (queue.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.manga_downloads_empty),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = stringResource(R.string.manga_downloads_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(queue, key = { it.key }) { download ->
                    QueueRow(
                        download = download,
                        info = queueInfo[download.key],
                        running = running,
                        onCancel = { scope.launch { manager.cancel(listOf(download.chapter)) } },
                        onRetry = { scope.launch { manager.retry(listOf(download.chapter)) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    download: MangaDownload,
    info: MangaDownloadInfo?,
    running: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = info?.state == MangaDownload.State.ERROR
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCoverImage(
            sourceId = download.manga.sourceId,
            url = download.manga.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(40.dp)
                .aspectRatio(2f / 3f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = download.manga.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = download.chapter.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (info != null && info.state == MangaDownload.State.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { (info.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 4.dp),
                )
            }
            Text(
                text = queueStatus(info, running),
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (failed) {
            IconButton(onClick = onRetry) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.manga_download_retry),
                )
            }
        } else {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.manga_download_cancel),
                )
            }
        }
    }
}

@Composable
private fun queueStatus(info: MangaDownloadInfo?, running: Boolean): String = when {
    info?.state == MangaDownload.State.DOWNLOADING -> stringResource(R.string.manga_downloads_running)
    info?.state == MangaDownload.State.ERROR -> stringResource(R.string.manga_downloads_failed)
    !running -> stringResource(R.string.manga_downloads_paused)
    else -> stringResource(R.string.manga_downloads_queued)
}
