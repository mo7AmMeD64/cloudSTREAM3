package com.kodastream.app.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import java.util.Locale

/** What the card downloads: one episode or movie, or a whole season by preference. */
sealed interface DownloadTarget {
    data class Single(
        val data: String,
        val season: Int?,
        val episode: Int?,
        val episodeTitle: String?,
    ) : DownloadTarget

    data class Season(
        val episodes: List<Episode>,
    ) : DownloadTarget
}

private sealed interface SheetState {
    data object Loading : SheetState
    data object Failed : SheetState
    data class Ready(
        val links: List<ExtractorLink>,
        val subtitles: List<SubtitleFile>,
    ) : SheetState
}

/**
 * The "Download options" card. It opens immediately and resolves the servers
 * inside, using the exact extraction the player uses, so tapping Download
 * never waits on the network before the card appears. The request is
 * cancelled with the card when it is dismissed.
 *
 * Server rules: radio list, last used server preselected, first as fallback.
 * Subtitle rules: none -> the section is hidden; one -> taken silently with
 * an info line; several -> a list with "No subtitles", the app language
 * preselected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadOptionsSheet(
    apiName: String,
    title: String,
    target: DownloadTarget,
    onConfirm: (link: ExtractorLink, subtitle: SubtitleFile?) -> Unit,
    onConfirmSeason: (serverName: String, subtitleLang: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<SheetState>(SheetState.Loading) }
    var retryTick by remember { mutableIntStateOf(0) }
    var managerMenu by remember { mutableStateOf(false) }

    var selectedLink by remember { mutableStateOf<ExtractorLink?>(null) }
    var selectedSub by remember { mutableStateOf<SubtitleFile?>(null) }

    val managers = remember {
        orderDownloadManagers(
            installed = installedDownloadManagers(context),
            remembered = AppPrefs.externalDownloadManager(context),
        )
    }

    fun launchExternal(manager: DownloadManager?) {
        val link = selectedLink ?: return
        val file = downloadFileName(
            title = title,
            episodeLabel = (target as? DownloadTarget.Single)?.let(::episodeLabel),
            url = link.url,
            isM3u8 = link.type == ExtractorLinkType.M3U8,
        )
        val started = if (manager == null) {
            startDownloadChooser(context, link, file, title)
        } else {
            startDownloadWith(context, link, file, manager.packageName)
        }
        if (!started) {
            Toast.makeText(context, R.string.download_no_app, Toast.LENGTH_SHORT).show()
            return
        }
        if (manager != null) AppPrefs.setExternalDownloadManager(context, manager.packageName)
        managerMenu = false
        onDismiss()
    }

    fun copyText(text: String, toast: Int) {
        if (!copyToClipboard(context, title, text)) return
        // Android 13 shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
    }

    // The same server extraction the player runs. Season mode probes the
    // first episode; every episode is matched individually at confirm time.
    LaunchedEffect(target, retryTick) {
        state = SheetState.Loading
        val api = Engine.provider(apiName)
        val probeData = when (target) {
            is DownloadTarget.Single -> target.data
            is DownloadTarget.Season -> target.episodes.firstOrNull()?.data
        }
        if (api == null || probeData == null) {
            state = SheetState.Failed
            return@LaunchedEffect
        }
        // Providers resolve their extractors in parallel and invoke these
        // callbacks from several threads at once - the same reason the
        // player keeps its link list in a thread-safe state flow. A plain
        // list here corrupts and kills the app from the extension's thread.
        val links = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val subs = java.util.Collections.synchronizedList(mutableListOf<SubtitleFile>())
        try {
            Engine.guarded(api.loadLinksTimeoutMs ?: 120_000L) {
                api.loadLinks(
                    probeData, false,
                    { sub -> runCatching { subs.add(sub) } },
                    { link -> runCatching { links.add(link) } },
                )
            }
        } catch (t: Throwable) {
            state = SheetState.Failed
            return@LaunchedEffect
        }
        // Snapshot: late extractors may keep appending after loadLinks
        // returned, so the card never iterates the live lists.
        val linkSnapshot = synchronized(links) { links.toList() }
        val subSnapshot = synchronized(subs) { subs.toList() }
        val usable = linkSnapshot
            .filter { it.type == ExtractorLinkType.VIDEO || it.type == ExtractorLinkType.M3U8 }
            .sortedByDescending { it.quality }
        state = if (usable.isEmpty()) SheetState.Failed else SheetState.Ready(usable, subSnapshot)
    }

    // Preselect once the list lands.
    LaunchedEffect(state) {
        val ready = state as? SheetState.Ready ?: return@LaunchedEffect
        val lastName = AppPrefs.lastDownloadServer(context)
        selectedLink = ready.links.firstOrNull { it.name == lastName } ?: ready.links.first()
        selectedSub = when (ready.subtitles.size) {
            0 -> null
            1 -> ready.subtitles.first()
            else -> {
                val locale = java.util.Locale.getDefault().language
                ready.subtitles.firstOrNull { sub ->
                    sub.lang.equals(locale, ignoreCase = true) ||
                        sub.langTag?.startsWith(locale, ignoreCase = true) == true
                } ?: ready.subtitles.first()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.download_options_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            when (val current = state) {
                SheetState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Text(
                            text = stringResource(R.string.loading_servers),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SheetState.Failed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.download_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        FilledTonalButton(onClick = { retryTick++ }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                is SheetState.Ready -> {
                    // Scrollable list, capped so landscape never cuts it off.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        itemsIndexed(current.links, key = { _, link -> link.url }) { _, link ->
                            val quality = Qualities.getStringByInt(link.quality)
                            val type = if (link.type == ExtractorLinkType.M3U8) {
                                stringResource(R.string.dl_file_hls)
                            } else {
                                stringResource(R.string.dl_file_mp4)
                            }
                            RadioRow(
                                selected = selectedLink?.url == link.url,
                                title = link.name,
                                detail = listOfNotNull(
                                    quality.takeIf { it.isNotBlank() },
                                    type,
                                ).joinToString("  •  "),
                                onClick = { selectedLink = link },
                            )
                        }

                        when (current.subtitles.size) {
                            0 -> Unit // No subtitles from this source: nothing to ask.

                            1 -> item(key = "sub-line") {
                                Text(
                                    text = stringResource(R.string.download_subtitle_line, current.subtitles.first().lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                )
                            }

                            else -> {
                                item(key = "sub-header") {
                                    Text(
                                        text = stringResource(R.string.subtitles),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                                    )
                                }
                                item(key = "sub-none") {
                                    RadioRow(
                                        selected = selectedSub == null,
                                        title = stringResource(R.string.download_no_subtitle),
                                        detail = null,
                                        onClick = { selectedSub = null },
                                    )
                                }
                                itemsIndexed(current.subtitles, key = { _, sub -> sub.url }) { _, sub ->
                                    RadioRow(
                                        selected = selectedSub?.url == sub.url,
                                        title = sub.lang,
                                        detail = null,
                                        onClick = { selectedSub = sub },
                                    )
                                }
                            }
                        }
                    }

                    val link = selectedLink
                    if (target is DownloadTarget.Single && link != null) {
                        val canUseLink = isHttpUrl(link.url)
                        val headerLines = remember(link) {
                            downloadHeaderLines(link.headers, link.referer)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TextButton(
                                    onClick = { copyText(link.url, R.string.copy_link_toast) },
                                    enabled = canUseLink,
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.copy_link))
                                }
                                if (headerLines.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            copyText(
                                                headerLines.joinToString("\n"),
                                                R.string.copy_headers_toast,
                                            )
                                        },
                                        enabled = canUseLink,
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.copy_headers))
                                    }
                                }
                                Box {
                                    FilledTonalButton(
                                        onClick = {
                                            if (managers.isEmpty()) launchExternal(null)
                                            else managerMenu = true
                                        },
                                        enabled = canUseLink,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.download_with))
                                    }
                                    DropdownMenu(
                                        expanded = managerMenu,
                                        onDismissRequest = { managerMenu = false },
                                    ) {
                                        managers.forEach { manager ->
                                            DropdownMenuItem(
                                                text = { Text(manager.label) },
                                                leadingIcon = { ManagerIcon(manager.packageName) },
                                                onClick = { launchExternal(manager) },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.download_with_other)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = { launchExternal(null) },
                                        )
                                    }
                                }
                            }
                            if (link.type == ExtractorLinkType.M3U8) {
                                Text(
                                    text = stringResource(R.string.download_hls_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                val link = selectedLink ?: return@Button
                                AppPrefs.setLastDownloadServer(context, link.name)
                                if (target is DownloadTarget.Season) {
                                    onConfirmSeason(link.name, selectedSub?.lang)
                                } else {
                                    onConfirm(link, selectedSub)
                                }
                            },
                            enabled = selectedLink != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.download))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    title: String,
    detail: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ManagerIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    if (icon == null) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(icon).build(),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun episodeLabel(target: DownloadTarget.Single): String? {
    target.episodeTitle?.takeIf { it.isNotBlank() }?.let { return it }
    val season = target.season ?: return null
    val episode = target.episode ?: return null
    return String.format(Locale.ROOT, "S%02dE%02d", season, episode)
}
