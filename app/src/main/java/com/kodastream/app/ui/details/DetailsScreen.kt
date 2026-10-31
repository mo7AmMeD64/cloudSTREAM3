package com.kodastream.app.ui.details

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.CategoryResult
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.EpisodeQueues
import com.kodastream.app.data.FavoriteCategoryStore
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.LibraryStore.LibraryItem
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.engine.Engine
import com.kodastream.app.engine.readableMessage
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SkeletonBox
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.kodastream.app.ui.components.bouncySpring
import com.kodastream.app.ui.library.MoveToSheet
import com.kodastream.app.ui.theme.ArtworkStatusBarIcons
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Failed(val message: String) : DetailsState
    data class Ready(val response: LoadResponse) : DetailsState
}

class DetailsViewModel(val args: DetailsArgs) : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DetailsState.Loading
            val api = Engine.provider(args.apiName)
            if (api == null) {
                _state.value = DetailsState.Failed("Provider \"${args.apiName}\" is not loaded")
                return@launch
            }
            Engine.load(api, args.url).fold(
                onSuccess = { _state.value = DetailsState.Ready(it) },
                onFailure = { _state.value = DetailsState.Failed(it.readableMessage()) },
            )
        }
    }
}

/** The episodes of a response, grouped by dub status. Movies and live streams have none. */
private fun episodesOf(response: LoadResponse): Map<DubStatus, List<Episode>> = when (response) {
    is AnimeLoadResponse -> response.episodes.filterValues { it.isNotEmpty() }
    is TvSeriesLoadResponse -> if (response.episodes.isEmpty()) emptyMap() else mapOf(DubStatus.None to response.episodes)
    else -> emptyMap()
}

/** The `data` string that loadLinks() wants for something playable right away. */
private fun directDataOf(response: LoadResponse): String? = when (response) {
    is MovieLoadResponse -> response.dataUrl
    is LiveStreamLoadResponse -> response.dataUrl
    else -> null
}

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onOpenDownloads: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val args = viewModel.args

    when (val current = state) {
        DetailsState.Loading -> Box(Modifier.fillMaxSize()) {
            DetailsSkeleton()
            TopBar(
                title = args.title.orEmpty(),
                collapsed = true,
                onBack = onBack,
                onShare = null,
            )
        }

        is DetailsState.Failed -> Box(Modifier.fillMaxSize()) {
            ErrorBox(message = current.message, onRetry = viewModel::load)
            TopBar(
                title = args.title.orEmpty(),
                collapsed = true,
                onBack = onBack,
                onShare = null,
            )
        }

        is DetailsState.Ready -> DetailsContent(
            response = current.response,
            fallbackTitle = args.title,
            onBack = onBack,
            onOpenItem = onOpenItem,
            onPlay = onPlay,
            onOpenDownloads = onOpenDownloads,
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    collapsed: Boolean,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
) {
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "topBarAlpha")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f * barAlpha))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .graphicsLayer { alpha = barAlpha },
        )
        if (onShare != null) {
            FilledTonalIconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.cd_share))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsContent(
    response: LoadResponse,
    fallbackTitle: String?,
    onBack: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val title = response.name.ifBlank { fallbackTitle.orEmpty() }
    val isMovie = response !is TvSeriesLoadResponse && response !is AnimeLoadResponse
    val directData = directDataOf(response)?.takeIf { it.isNotBlank() }

    val episodesByDub = remember(response) { episodesOf(response) }
    val dubOptions = remember(episodesByDub) { episodesByDub.keys.toList() }
    var selectedDub by remember(response) { mutableStateOf(dubOptions.firstOrNull()) }
    val allEpisodes = episodesByDub[selectedDub].orEmpty()
    val allEpisodesAnyDub = remember(episodesByDub) { episodesByDub.values.flatten() }

    val seasonNames = remember(response) { (response as? EpisodeResponse)?.seasonNames.orEmpty() }
    val seasons = remember(allEpisodes) { allEpisodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by remember(response, selectedDub) { mutableStateOf(seasons.firstOrNull()) }
    val visibleEpisodes = if (selectedSeason == null) {
        allEpisodes
    } else {
        allEpisodes.filter { it.season == selectedSeason || it.season == null }
    }

    // Watched and history are read once for the screen; both only change after
    // an explicit action or when playback returns, never per download tick.
    var watchedKeys by remember { mutableStateOf(WatchedStore.current(context)) }
    LaunchedEffect(Unit) {
        WatchedStore.keys.collect { updated -> updated?.let { watchedKeys = it } }
    }
    var history by remember { mutableStateOf(HistoryStore.current(context)) }
    LaunchedEffect(Unit) {
        HistoryStore.entries.collect { updated -> updated?.let { history = it } }
    }
    var hideWatched by rememberSaveable { mutableStateOf(false) }
    // Latest is a request, not a position: the effect that owns each list
    // scrolls once the new season's episodes are on screen.
    var scrollToLatest by remember { mutableStateOf(0) }

    // Library state is derived once per revision, not once per toggle button.
    val revision by LibraryStore.revision.collectAsState()
    val libraryItem = remember(response) {
        LibraryItem(
            apiName = response.apiName,
            url = response.url,
            name = response.name,
            posterUrl = response.posterUrl,
            posterHeaders = response.posterHeaders,
            score10 = response.score?.toFloat(10),
        )
    }
    val isFavorite = remember(revision, libraryItem) {
        LibraryStore.isFavorite(context, libraryItem.apiName, libraryItem.url)
    }
    val isWatchlisted = remember(revision, libraryItem) {
        LibraryStore.isWatchlisted(context, libraryItem.apiName, libraryItem.url)
    }
    val favoriteCategories = remember(revision) { FavoriteCategoryStore.categories(context) }
    val favoriteCategoryId = remember(revision, libraryItem) {
        FavoriteCategoryStore.categoryOf(context, libraryItem.apiName, libraryItem.url)
    }
    var moveFavoriteSheet by remember { mutableStateOf(false) }

    // Only lifecycle changes reach the screen; byte progress is never read, so
    // a progress tick cannot invalidate the whole page.
    val initialDownloadStates = remember(context) {
        DownloadStore.current(context).associate { it.id to it.state }
    }
    val downloadStates = remember(context) {
        DownloadStore.entries
            .map { list -> list.orEmpty().associate { it.id to it.state } }
            .distinctUntilChanged()
    }.collectAsState(initial = initialDownloadStates)

    fun episodeKey(episode: Episode, index: Int): String =
        WatchedStore.keyOf(response.apiName, response.url, episode.season ?: selectedSeason, episode.episode ?: index + 1)

    /** Marks a batch of episodes and keeps the title's History entry in step. */
    fun setWatchedBatch(episodes: List<Pair<Episode, Int>>, watched: Boolean) {
        WatchedStore.setAll(context, episodes.map { (ep, i) -> episodeKey(ep, i) }, watched)
        val historyKey = response.apiName + "|" + response.url
        if (watched) {
            HistoryStore.setCompleted(context, historyKey, true)
        } else {
            HistoryStore.setCompleted(context, historyKey, false)
            HistoryStore.resetProgress(context, historyKey)
        }
    }

    val plain = remember(response) {
        response.plot?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
    }
    val historyKey = response.apiName + "|" + response.url
    val resume = remember(history, response) {
        history.firstOrNull { it.key == historyKey && !it.isComplete && it.data.isNotBlank() }
    }
    val resumeEpisode = remember(resume, allEpisodesAnyDub) {
        resume?.let { entry ->
            allEpisodesAnyDub.firstOrNull { it.data == entry.data }
                ?: allEpisodesAnyDub.firstOrNull { it.season == entry.season && it.episode == entry.episode }
        }
    }
    val resumeFraction = resume
        ?.takeIf { it.durationMs > 0L }
        ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        ?: 0f
    val resumeMinutes = resume
        ?.takeIf { it.durationMs > it.positionMs }
        ?.let { ((it.durationMs - it.positionMs) / 60_000L).coerceAtLeast(1L) }
    val firstPlayable = allEpisodes.firstOrNull() ?: allEpisodesAnyDub.firstOrNull()
    val firstPlayableLabel = if (firstPlayable != null) episodeLabel(firstPlayable, 0) else null

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var downloadSheet by remember { mutableStateOf<DownloadTarget?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startDownload(
        link: ExtractorLink,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        episodeData: String? = null,
        subtitles: List<SubtitleFile> = emptyList(),
    ) {
        ensureNotifPermission()
        val isHls = link.type == ExtractorLinkType.M3U8
        val sanitize: (String) -> String = { s: String ->
            s.replace(Regex("[\\/:*?\"<>|]"), "").trim().take(100)
        }
        val fileName = if (season != null || episode != null) {
            "${sanitize(title)} - S${"%02d".format(season ?: 1)}E${"%02d".format(episode ?: 1)}" +
                (episodeTitle?.takeIf { it.isNotBlank() }?.let { " - ${sanitize(it)}" } ?: "") +
                "." + (if (isHls) "ts" else "mp4")
        } else {
            sanitize(title) + (response.year?.let { " ($it)" } ?: "") + "." + (if (isHls) "ts" else "mp4")
        }
        val queued = DownloadStore.enqueue(
            context,
            DownloadStore.Download(
                id = DownloadStore.idOf(response.apiName, response.url, season, episode),
                apiName = response.apiName,
                url = response.url,
                title = title,
                posterUrl = response.posterUrl,
                posterHeaders = response.posterHeaders,
                type = when (response) {
                    is AnimeLoadResponse -> "anime"
                    is TvSeriesLoadResponse -> "series"
                    else -> "movie"
                },
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                year = response.year,
                linkUrl = link.url,
                linkName = link.name,
                quality = link.quality,
                headers = link.getAllHeaders(),
                streamType = if (isHls) "hls" else "direct",
                data = episodeData,
                fileName = fileName,
                subLang = subtitles.firstOrNull()?.lang,
                subUrl = subtitles.firstOrNull()?.url,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (queued) DownloadService.ensure(context)
    }

    /** Moves the title and offers Undo for the previous category. */
    fun moveFavoriteTo(categoryId: String?, knownName: String? = null) {
        moveFavoriteSheet = false
        val key = LibraryStore.keyOf(libraryItem)
        val before = FavoriteCategoryStore.categoryOf(context, libraryItem.apiName, libraryItem.url)
        val name = knownName ?: favoriteCategories.firstOrNull { it.id == categoryId }?.name
            ?: context.getString(R.string.favorites_category_uncategorized)
        FavoriteCategoryStore.move(context, listOf(key), categoryId)
        scope.launch {
            val message = context.resources.getQuantityString(R.plurals.favorites_moved, 1, 1, name)
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.history_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                FavoriteCategoryStore.move(context, listOf(key), before)
            }
        }
    }

    fun createCategoryAndMove(rawName: String) {
        moveFavoriteSheet = false
        when (val result = FavoriteCategoryStore.create(context, rawName)) {
            is CategoryResult.Created -> moveFavoriteTo(result.category.id, result.category.name)
            CategoryResult.LimitReached -> scope.launch {
                snackbar.showSnackbar(context.getString(R.string.favorites_category_limit))
            }
            else -> Unit
        }
    }

    /** "Download added" with a way to jump straight to the Downloads tab. */
    fun announceDownload(count: Int, fallbacks: Int) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = if (fallbacks > 0) {
                    context.getString(R.string.download_season_fallback, count, fallbacks)
                } else {
                    context.getString(R.string.added_to_downloads)
                },
                actionLabel = context.getString(R.string.view),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                AppEvents.pendingLibrarySection.value = "DOWNLOADS"
                onOpenDownloads()
            }
        }
    }

    /** Season mode: apply the preferred server and language to every episode. */
    fun downloadSeason(episodes: List<Episode>, preferredServer: String, preferredSubLang: String?) {
        scope.launch {
            val api = Engine.provider(response.apiName) ?: return@launch
            var queued = 0
            var fallbacks = 0
            for ((index, episode) in episodes.withIndex()) {
                val season = episode.season ?: selectedSeason
                val number = episode.episode ?: index + 1
                val id = DownloadStore.idOf(response.apiName, response.url, season, number)
                if (DownloadStore.current(context).any { it.id == id && it.state == DownloadStore.STATE_DONE }) continue
                // Thread-safe for the same reason as the download card:
                // providers fire these callbacks from parallel extractors.
                val links = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
                val subs = java.util.Collections.synchronizedList(mutableListOf<SubtitleFile>())
                try {
                    Engine.guarded(api.loadLinksTimeoutMs ?: 120_000L) {
                        api.loadLinks(
                            episode.data, false,
                            { sub -> runCatching { subs.add(sub) } },
                            { link -> runCatching { links.add(link) } },
                        )
                    }
                } catch (t: Throwable) {
                    continue
                }
                val usable = synchronized(links) { links.toList() }
                    .filter { it.type == ExtractorLinkType.VIDEO || it.type == ExtractorLinkType.M3U8 }
                val chosen = usable.firstOrNull { it.name.equals(preferredServer, ignoreCase = true) }
                    ?: usable.maxByOrNull { it.quality }
                    ?: continue
                if (!chosen.name.equals(preferredServer, ignoreCase = true)) fallbacks++
                val sub = if (preferredSubLang == null) {
                    null
                } else {
                    synchronized(subs) { subs.toList() }.firstOrNull { candidate ->
                        candidate.lang.equals(preferredSubLang, ignoreCase = true) ||
                            candidate.langTag?.startsWith(preferredSubLang, ignoreCase = true) == true
                    }
                }
                if (preferredSubLang != null && sub == null) fallbacks++
                startDownload(
                    chosen, season, number, episode.name, episode.data,
                    sub?.let { listOf(it) } ?: emptyList(),
                )
                queued++
            }
            if (queued > 0) announceDownload(queued, fallbacks)
        }
    }

    fun play(data: String, subtitle: String?, episode: Episode? = null) {
        val resumeKey = response.apiName + "|" + data
        // Episodes carry the whole series with them so the player can walk
        // between episodes without loading anything again.
        val queue = if (episode != null && allEpisodes.isNotEmpty()) {
            EpisodeQueues.build(
                apiName = response.apiName,
                url = response.url,
                title = title,
                posterUrl = response.posterUrl,
                posterHeaders = response.posterHeaders,
                type = when (response) {
                    is AnimeLoadResponse -> "anime"
                    is TvSeriesLoadResponse -> "series"
                    else -> "movie"
                },
                episodes = allEpisodes,
            )
        } else {
            null
        }
        onPlay(
            PlayerArgs(
                apiName = response.apiName,
                data = data,
                title = title,
                subtitle = subtitle,
                resumeKey = resumeKey,
                queue = queue,
                history = HistoryEntry(
                    key = historyKey,
                    apiName = response.apiName,
                    url = response.url,
                    title = title,
                    posterUrl = response.posterUrl,
                    posterHeaders = response.posterHeaders,
                    type = when (response) {
                        is AnimeLoadResponse -> "anime"
                        is TvSeriesLoadResponse -> "series"
                        else -> "movie"
                    },
                    season = episode?.season ?: selectedSeason,
                    episode = episode?.episode,
                    episodeTitle = episode?.name,
                    data = data,
                    subtitle = subtitle,
                    resumeKey = resumeKey,
                ),
            )
        )
    }

    val movieKey = if (isMovie) WatchedStore.keyOf(response.apiName, response.url, null, null) else null
    val movieWatched = movieKey != null && movieKey in watchedKeys
    val movieDownloadId = if (isMovie && directData != null) {
        DownloadStore.idOf(response.apiName, response.url, null, null)
    } else {
        null
    }

    val share: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, title, response.url))
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
        }
    }

    val primaryTrailerUrl = remember(response) {
        response.trailers.firstNotNullOfOrNull(::trailerUrl)
    }
    val playLabel = when {
        response.comingSoon -> stringResource(R.string.coming_soon)
        resume != null -> stringResource(R.string.resume)
        else -> stringResource(R.string.play)
    }
    val playSubtitle = if (resume != null && resumeMinutes != null) {
        stringResource(R.string.minutes_left, resumeMinutes)
    } else {
        null
    }
    val playEnabled = !response.comingSoon && (directData != null || firstPlayable != null)
    val playPrimary: () -> Unit = {
        when {
            resume != null -> play(resume.data, resume.subtitle, resumeEpisode)
            directData != null -> play(directData, null)
            firstPlayable != null -> play(firstPlayable.data, firstPlayableLabel, firstPlayable)
        }
    }

    val tags = remember(response) { response.tags.orEmpty().filter { it.isNotBlank() } }
    val recommendations = remember(response) {
        response.recommendations.orEmpty().distinctBy { it.apiName to it.url }
    }
    val actors = remember(response) { response.actors.orEmpty() }

    // The page as reusable groups, so phones can keep their single running
    // list while the tablet splits the same groups across two panes.
    val summaryItems: LazyListScope.() -> Unit = {
        if (tags.isNotEmpty()) {
            item(key = "tags") { TagsRow(tags) }
        }

        item(key = "actions") {
            val downloadState = movieDownloadId?.let { downloadStates.value[it] }
            PrimaryActions(
                playLabel = playLabel,
                playSubtitle = playSubtitle,
                progress = resumeFraction,
                playEnabled = playEnabled,
                onPlay = playPrimary,
                favorite = isFavorite,
                onFavorite = {
                    val nowFavorite = LibraryStore.toggleFavorite(context, libraryItem)
                    if (nowFavorite && favoriteCategories.isNotEmpty()) {
                        scope.launch {
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.favorites_added),
                                actionLabel = context.getString(R.string.favorites_choose_category),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                moveFavoriteSheet = true
                            }
                        }
                    }
                },
                onFavoriteLongClick = {
                    if (!LibraryStore.isFavorite(context, response.apiName, response.url)) {
                        LibraryStore.toggleFavorite(context, libraryItem)
                    }
                    moveFavoriteSheet = true
                },
                watchlisted = isWatchlisted,
                onWatchlist = { LibraryStore.toggleWatchlist(context, libraryItem) },
                showDownload = isMovie && directData != null,
                downloadState = downloadState,
                onDownload = {
                    when (downloadState) {
                        null -> if (directData != null) {
                            downloadSheet = DownloadTarget.Single(directData, null, null, null)
                        }

                        DownloadStore.STATE_DOWNLOADING -> movieDownloadId?.let { DownloadService.pause(context, it) }
                        DownloadStore.STATE_PAUSED -> movieDownloadId?.let { DownloadService.resume(context, it) }
                        DownloadStore.STATE_FAILED -> movieDownloadId?.let { DownloadService.retry(context, it) }
                        DownloadStore.STATE_QUEUED -> movieDownloadId?.let { DownloadService.cancel(context, it) }
                        DownloadStore.STATE_DONE -> Unit
                    }
                },
                watched = if (isMovie) movieWatched else null,
                onToggleWatched = {
                    if (movieKey != null) {
                        WatchedStore.setWatched(
                            context = context,
                            apiName = response.apiName,
                            url = response.url,
                            season = null,
                            episode = null,
                            resumeKey = null,
                            watched = !movieWatched,
                        )
                    }
                },
                trailerUrl = primaryTrailerUrl,
                onTrailer = {
                    primaryTrailerUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
                },
                onShare = share,
            )
        }

        if (!plain.isNullOrBlank()) {
            item(key = "overview") { Overview(plain) }
        }
    }

    val castItem: LazyListScope.() -> Unit = {
        if (actors.isNotEmpty()) {
            item(key = "cast") {
                SectionTitle(stringResource(R.string.cast_title))
                CastRail(actors.take(20), response.posterHeaders)
            }
        }
    }

    val allWatched = visibleEpisodes.isNotEmpty() && visibleEpisodes.indices.all { i ->
        episodeKey(visibleEpisodes[i], i) in watchedKeys
    }
    val toggleSeasonWatched: () -> Unit = {
        setWatchedBatch(visibleEpisodes.mapIndexed { i, ep -> ep to i }, !allWatched)
    }

    val episodeHeader: @Composable (Boolean) -> Unit = { withSeasonAction ->
        EpisodeHeader(
            dubOptions = dubOptions,
            selectedDub = selectedDub,
            onSelectDub = { selectedDub = it },
            seasons = seasons,
            selectedSeason = selectedSeason,
            seasonNames = seasonNames,
            allEpisodes = allEpisodes,
            onSelectSeason = { selectedSeason = it },
            onLatest = {
                selectedSeason = seasons.lastOrNull()
                scrollToLatest++
            },
            hideWatched = hideWatched,
            onMarkPreviousWatched = {
                // Everything before the highest watched episode.
                val maxWatched = visibleEpisodes.mapIndexedNotNull { i, ep ->
                    if (episodeKey(ep, i) in watchedKeys) (ep.episode ?: i + 1) else null
                }.maxOrNull()
                if (maxWatched != null) {
                    setWatchedBatch(
                        visibleEpisodes.mapIndexed { i, ep -> ep to i }
                            .filter { (ep, i) -> (ep.episode ?: i + 1) < maxWatched },
                        true,
                    )
                }
            },
            onDownloadSeason = {
                downloadSheet = DownloadTarget.Season(visibleEpisodes.toList())
            },
            onToggleHideWatched = { hideWatched = !hideWatched },
            seasonAction = if (withSeasonAction) {
                { SeasonWatchedAction(allWatched = allWatched, onClick = toggleSeasonWatched) }
            } else {
                null
            },
        )
    }

    val episodeCards: LazyListScope.() -> Unit = {
        itemsIndexed(
            items = visibleEpisodes,
            key = { index, episode ->
                "${episode.season}:${episode.episode ?: index}:${episode.data.hashCode()}"
            },
            contentType = { _, _ -> "episode" },
        ) { index, episode ->
            val watched = episodeKey(episode, index) in watchedKeys
            // Hidden watched episodes keep their place in the index math,
            // they just draw nothing while the toggle is on.
            if (watched && hideWatched) return@itemsIndexed
            // A composable call, so it is made here and not inside the click handler.
            val label = episodeLabel(episode, index)
            val downloadId = DownloadStore.idOf(
                response.apiName, response.url,
                episode.season ?: selectedSeason, episode.episode ?: index + 1,
            )
            val downloadState = downloadStates.value[downloadId]
            val seasonIndex = seasons.indexOf(selectedSeason)
            EpisodeCard(
                episode = episode,
                index = index,
                posterHeaders = response.posterHeaders,
                watched = watched,
                downloadId = downloadId,
                downloadState = downloadState,
                resumeKey = response.apiName + "|" + episode.data,
                history = history,
                onClick = { play(episode.data, label, episode) },
                onToggleWatched = {
                    WatchedStore.setWatched(
                        context = context,
                        apiName = response.apiName,
                        url = response.url,
                        season = episode.season ?: selectedSeason,
                        episode = episode.episode ?: index + 1,
                        resumeKey = response.apiName + "|" + episode.data,
                        watched = !watched,
                    )
                },
                onDownload = {
                    when (downloadState) {
                        null -> downloadSheet = DownloadTarget.Single(
                            episode.data,
                            episode.season ?: selectedSeason,
                            episode.episode ?: index + 1,
                            episode.name,
                        )

                        DownloadStore.STATE_DOWNLOADING -> DownloadService.pause(context, downloadId)
                        DownloadStore.STATE_PAUSED -> DownloadService.resume(context, downloadId)
                        DownloadStore.STATE_FAILED -> DownloadService.retry(context, downloadId)
                        DownloadStore.STATE_QUEUED -> DownloadService.cancel(context, downloadId)
                        DownloadStore.STATE_DONE -> Unit
                    }
                },
                onDownloadLongPress = {
                    downloadSheet = DownloadTarget.Single(
                        episode.data,
                        episode.season ?: selectedSeason,
                        episode.episode ?: index + 1,
                        episode.name,
                    )
                },
                modifier = if (seasons.size > 1) {
                    Modifier.seasonSwipe(
                        onPrevious = {
                            selectedSeason = seasons.getOrNull(seasonIndex - 1) ?: selectedSeason
                        },
                        onNext = {
                            selectedSeason = seasons.getOrNull(seasonIndex + 1) ?: selectedSeason
                        },
                    )
                } else {
                    Modifier
                },
            )
        }
    }

    val unsupportedItem: LazyListScope.() -> Unit = {
        item(key = "unsupported") {
            Text(
                text = stringResource(
                    if (response.comingSoon) R.string.coming_soon else R.string.type_not_supported
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    val railItems: LazyListScope.() -> Unit = {
        if (response.trailers.isNotEmpty()) {
            item(key = "trailers") {
                SectionTitle(stringResource(R.string.trailers_title))
                TrailerRail(response.trailers)
            }
        }

        if (recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                Column {
                    SectionTitle(stringResource(R.string.recommended))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = recommendations,
                            key = { _, item -> "${item.apiName}|${item.url}" },
                            contentType = { _, _ -> "poster" },
                        ) { _, item ->
                            PosterCard(
                                item = item,
                                onClick = { onOpenItem(item) },
                                cardWidth = 128.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 600.dp) {
                // Two panes from 600dp: the title page on the start side (40%,
                // capped), the episodes and rails on the end side.
                val startWidth = (maxWidth * 0.4f).coerceAtMost(440.dp)
                val startState = rememberLazyListState()
                val endState = rememberLazyListState()
                LaunchedEffect(scrollToLatest) {
                    if (scrollToLatest > 0 && visibleEpisodes.isNotEmpty()) {
                        endState.animateScrollToItem(visibleEpisodes.size)
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(startWidth)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            state = startState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            item(key = "hero") {
                                DetailsHero(
                                    response = response,
                                    title = title,
                                    height = 480.dp,
                                    parallax = { 0f },
                                )
                            }
                            summaryItems()
                            castItem()
                            item(key = "pane-bottom") { Spacer(Modifier.navigationBarsPadding()) }
                        }
                        TopBar(
                            title = title,
                            collapsed = false,
                            onBack = onBack,
                            onShare = share,
                        )
                        ArtworkStatusBarIcons(overArtwork = startState.firstVisibleItemIndex == 0)
                    }
                    LazyColumn(
                        state = endState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (visibleEpisodes.isNotEmpty()) {
                            stickyHeader(key = "episodes-header") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    episodeHeader(true)
                                }
                            }
                            episodeCards()
                        } else if (directData == null && episodesByDub.isEmpty()) {
                            unsupportedItem()
                        }
                        railItems()
                        item(key = "pane-bottom") { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                // hero + summary group + episodes header + the cards themselves.
                val summaryItemCount =
                    (if (tags.isNotEmpty()) 1 else 0) + 1 + (if (!plain.isNullOrBlank()) 1 else 0)
                LaunchedEffect(scrollToLatest) {
                    if (scrollToLatest > 0 && visibleEpisodes.isNotEmpty()) {
                        listState.animateScrollToItem(1 + summaryItemCount + visibleEpisodes.size)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "hero") {
                        DetailsHero(
                            response = response,
                            title = title,
                            parallax = {
                                if (listState.firstVisibleItemIndex == 0) {
                                    listState.firstVisibleItemScrollOffset / 2f
                                } else {
                                    0f
                                }
                            },
                        )
                    }

                    summaryItems()

                    if (visibleEpisodes.isNotEmpty()) {
                        item(key = "episodes-header") { episodeHeader(false) }
                        episodeCards()
                        item(key = "season-watched") {
                            SeasonWatchedAction(
                                allWatched = allWatched,
                                onClick = toggleSeasonWatched,
                            )
                        }
                    } else if (directData == null && episodesByDub.isEmpty()) {
                        unsupportedItem()
                    }

                    castItem()
                    railItems()

                    item(key = "bottom-space") { Spacer(Modifier.navigationBarsPadding()) }
                }

                TopBar(
                    title = title,
                    collapsed = collapsed,
                    onBack = onBack,
                    onShare = share,
                )
                ArtworkStatusBarIcons(overArtwork = !collapsed)
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    downloadSheet?.let { sheetTarget ->
        DownloadOptionsSheet(
            apiName = response.apiName,
            title = title,
            target = sheetTarget,
            onConfirm = { link, subtitle ->
                if (sheetTarget is DownloadTarget.Single) {
                    startDownload(
                        link,
                        sheetTarget.season,
                        sheetTarget.episode,
                        sheetTarget.episodeTitle,
                        sheetTarget.data,
                        subtitle?.let { listOf(it) } ?: emptyList(),
                    )
                    announceDownload(1, 0)
                }
                downloadSheet = null
            },
            onConfirmSeason = { serverName, subLang ->
                downloadSheet = null
                if (sheetTarget is DownloadTarget.Season) {
                    downloadSeason(sheetTarget.episodes, serverName, subLang)
                }
            },
            onDismiss = { downloadSheet = null },
        )
    }

    if (moveFavoriteSheet) {
        MoveToSheet(
            categories = favoriteCategories,
            selectedCategoryIds = setOf(favoriteCategoryId),
            onDismiss = { moveFavoriteSheet = false },
            onMove = { categoryId -> moveFavoriteTo(categoryId) },
            onCreateAndMove = { name -> createCategoryAndMove(name) },
        )
    }
}

@Composable
private fun DetailsHero(
    response: LoadResponse,
    title: String,
    height: Dp = 540.dp,
    parallax: () -> Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // The parallax shifts the artwork down; without clipping its bottom edge shows below the hero.
            .clipToBounds()
    ) {
        RemoteImage(
            url = response.backgroundPosterUrl ?: response.posterUrl,
            headers = response.posterHeaders,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = parallax() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Reaching full background before the title block, so the
                    // text never sits on a bright poster in Light.
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        0.3f to Color.Transparent,
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        0.72f to MaterialTheme.colorScheme.background,
                        1f to MaterialTheme.colorScheme.background,
                    )
                )
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
                RemoteImage(
                    url = response.posterUrl,
                    headers = response.posterHeaders,
                    contentDescription = stringResource(R.string.cd_poster, title),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                if (response.comingSoon) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.coming_soon).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
                DetailsMetaLine(response, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DetailsMetaLine(response: LoadResponse, modifier: Modifier = Modifier) {
    val minutes = response.duration?.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, it) }
    val parts = listOfNotNull(
        response.year?.toString(),
        minutes,
        response.contentRating?.takeIf { it.isNotBlank() },
    )
    val score = response.score?.toFloat(10)?.takeIf { it > 0f }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (score != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = " %.1f".format(score) + if (parts.isNotEmpty()) "  ·  " else "",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
    ) {
        tags.take(4).forEach { tag ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private data class ActionTileSpec(
    val icon: ImageVector,
    val label: String,
    val active: Boolean = false,
    val busy: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

@Composable
private fun PrimaryActions(
    playLabel: String,
    playSubtitle: String?,
    progress: Float,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onFavoriteLongClick: () -> Unit,
    watchlisted: Boolean,
    onWatchlist: () -> Unit,
    showDownload: Boolean,
    downloadState: String?,
    onDownload: () -> Unit,
    watched: Boolean?,
    onToggleWatched: () -> Unit,
    trailerUrl: String?,
    onTrailer: () -> Unit,
    onShare: () -> Unit,
) {
    val favoriteLabel = stringResource(R.string.action_favorite)
    val watchlistLabel = stringResource(R.string.action_watchlist)
    val downloadLabel = stringResource(R.string.download)
    val queuedLabel = stringResource(R.string.download_queued)
    val downloadingLabel = stringResource(R.string.downloading)
    val pausedLabel = stringResource(R.string.download_paused)
    val failedLabel = stringResource(R.string.download_failed)
    val downloadedLabel = stringResource(R.string.downloaded)
    val watchedLabel = stringResource(R.string.cd_watched)
    val markWatchedLabel = stringResource(R.string.mark_watched)
    val trailerLabel = stringResource(R.string.action_trailer)
    val shareLabel = stringResource(R.string.action_share)

    val tiles = buildList {
        add(
            ActionTileSpec(
                icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = favoriteLabel,
                active = favorite,
                onClick = onFavorite,
                onLongClick = onFavoriteLongClick,
            )
        )
        add(
            ActionTileSpec(
                icon = if (watchlisted) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = watchlistLabel,
                active = watchlisted,
                onClick = onWatchlist,
            )
        )
        if (showDownload) {
            add(
                when (downloadState) {
                    null -> ActionTileSpec(Icons.Rounded.Download, downloadLabel, onClick = onDownload)
                    DownloadStore.STATE_QUEUED -> ActionTileSpec(
                        Icons.Rounded.Schedule, queuedLabel, active = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_DOWNLOADING -> ActionTileSpec(
                        Icons.Rounded.Downloading, downloadingLabel, busy = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_PAUSED -> ActionTileSpec(
                        Icons.Rounded.PauseCircle, pausedLabel, active = true, onClick = onDownload,
                    )
                    DownloadStore.STATE_FAILED -> ActionTileSpec(
                        Icons.Rounded.ErrorOutline, failedLabel, onClick = onDownload,
                    )
                    DownloadStore.STATE_DONE -> ActionTileSpec(
                        Icons.Rounded.DownloadDone, downloadedLabel, active = true, onClick = onDownload,
                    )
                    else -> ActionTileSpec(Icons.Rounded.Download, downloadLabel, onClick = onDownload)
                }
            )
        }
        if (watched != null) {
            add(
                ActionTileSpec(
                    icon = if (watched) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                    label = if (watched) watchedLabel else markWatchedLabel,
                    active = watched,
                    onClick = onToggleWatched,
                )
            )
        }
        if (trailerUrl != null) {
            add(ActionTileSpec(Icons.Rounded.Theaters, trailerLabel, onClick = onTrailer))
        }
        add(ActionTileSpec(Icons.Rounded.Share, shareLabel, onClick = onShare))
    }

    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Button(
            onClick = onPlay,
            enabled = playEnabled,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = playLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playSubtitle != null) {
                    Text(
                        text = playSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (progress > 0f) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        tiles.chunked(3).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (rowIndex == 0) 12.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTiles.forEach { tile ->
                    ActionTile(tile = tile, modifier = Modifier.weight(1f))
                }
                repeat(3 - rowTiles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    tile: ActionTileSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (tile.active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (tile.active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = tile.onClick, onLongClick = tile.onLongClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tile.busy) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                AnimatedContent(
                    targetState = tile.icon,
                    transitionSpec = {
                        (scaleIn(bouncySpring()) + fadeIn()).togetherWith(scaleOut() + fadeOut())
                    },
                    label = "actionTileIcon",
                ) { icon ->
                    Icon(icon, contentDescription = null)
                }
            }
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Overview(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .animateContentSize()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 220) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(
                    stringResource(
                        if (expanded) R.string.show_less else R.string.more
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 14.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CastRail(actors: List<ActorData>, posterHeaders: Map<String, String>?) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = actors,
            key = { index, person -> "${person.actor.name}-$index" },
            contentType = { _, _ -> "cast" },
        ) { _, person ->
            Column(
                modifier = Modifier.width(88.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RemoteImage(
                    url = person.actor.image,
                    headers = posterHeaders,
                    contentDescription = person.actor.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                )
                Text(
                    text = person.actor.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val role = person.roleString ?: person.role?.name
                if (!role.isNullOrBlank()) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrailerRail(trailers: List<TrailerData>) {
    val uriHandler = LocalUriHandler.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = trailers,
            key = { index, trailer -> "${trailer.extractorUrl}-$index" },
            contentType = { _, _ -> "trailer" },
        ) { index, trailer ->
            val url = trailerUrl(trailer)
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        onClickLabel = stringResource(R.string.cd_trailer, index + 1),
                    ) {
                        if (url != null) runCatching { uriHandler.openUri(url) }
                    }
                    .padding(bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.trailer_n, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
                )
            }
        }
    }
}

private fun trailerUrl(trailer: TrailerData): String? {
    val raw = trailer.extractorUrl.takeIf { it.isNotBlank() } ?: return null
    return if (raw.startsWith("http")) raw else "https://www.youtube.com/watch?v=$raw"
}

@Composable
private fun episodeLabel(episode: Episode, index: Int): String {
    val number = episode.episode ?: (index + 1)
    val base = stringResource(R.string.episode_n, number)
    return if (episode.name.isNullOrBlank()) base else "$base • ${episode.name}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episode: Episode,
    index: Int,
    posterHeaders: Map<String, String>?,
    watched: Boolean,
    downloadId: String,
    downloadState: String?,
    resumeKey: String,
    history: List<HistoryEntry>,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownload: () -> Unit,
    onDownloadLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val number = episode.episode ?: (index + 1)
    val title = episode.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.episode_n, number)
    val markWatchedLabel = stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched)
    val downloadLabel = stringResource(R.string.download)
    val chooseSourceLabel = stringResource(R.string.download_pick_quality)
    val playLabel = stringResource(R.string.play)
    val moreLabel = stringResource(R.string.episode_actions)
    var menuOpen by remember { mutableStateOf(false) }

    val description = remember(episode.description) {
        episode.description
            ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
            ?.takeIf { it.isNotBlank() }
    }
    val meta = remember(episode.runTime, episode.date, context) {
        val runtime = episode.runTime
            ?.takeIf { it > 0 }
            ?.let { context.getString(R.string.minutes_short, it) }
        val date = episode.date?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) }
        listOfNotNull(runtime, date).joinToString(" · ")
    }
    // History changes after playback, so it both keys the read and refreshes it.
    val progress = remember(resumeKey, history) {
        val duration = AppPrefs.duration(context, resumeKey)
        val position = AppPrefs.position(context, resumeKey)
        if (duration > 0L && position >= 5_000L) {
            (position.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
                onClickLabel = playLabel,
                onLongClickLabel = moreLabel,
            )
            .semantics(mergeDescendants = true) {
                customActions = listOf(
                    CustomAccessibilityAction(markWatchedLabel) { onToggleWatched(); true },
                    CustomAccessibilityAction(downloadLabel) { onDownload(); true },
                    CustomAccessibilityAction(chooseSourceLabel) { onDownloadLongPress(); true },
                )
            }
            .padding(8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    RemoteImage(
                        url = episode.posterUrl,
                        headers = posterHeaders,
                        contentScale = ContentScale.Crop,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxSize(),
                    )
                    EpisodeThumbnailOverlay(number = number, watched = watched, progress = progress)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                EpisodeDownloadAction(
                    downloadId = downloadId,
                    downloadState = downloadState,
                    onDownload = onDownload,
                    onDownloadLongPress = onDownloadLongPress,
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(markWatchedLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleWatched()
                },
            )
            DropdownMenuItem(
                text = { Text(downloadLabel) },
                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDownload()
                },
            )
            DropdownMenuItem(
                text = { Text(chooseSourceLabel) },
                onClick = {
                    menuOpen = false
                    onDownloadLongPress()
                },
            )
        }
    }
}

@Composable
private fun BoxScope.EpisodeThumbnailOverlay(
    number: Int,
    watched: Boolean,
    progress: Float,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp),
    ) {
        Text(
            text = stringResource(R.string.episode_badge, number),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    when {
        watched -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.cd_watched),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }

        progress > 0f -> LinearWavyProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun EpisodeDownloadAction(
    downloadId: String,
    downloadState: String?,
    onDownload: () -> Unit,
    onDownloadLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val clickLabel = stringResource(
        when (downloadState) {
            DownloadStore.STATE_DOWNLOADING -> R.string.cd_download_pause
            DownloadStore.STATE_PAUSED -> R.string.cd_download_resume
            DownloadStore.STATE_FAILED -> R.string.cd_download_retry
            DownloadStore.STATE_QUEUED -> R.string.cd_download_cancel
            DownloadStore.STATE_DONE -> R.string.downloaded
            else -> R.string.cd_download
        }
    )
    val longLabel = stringResource(R.string.download_options_title)
    val clickModifier = Modifier
        .size(48.dp)
        .clip(MaterialTheme.shapes.medium)
        .combinedClickable(
            onClick = onDownload,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDownloadLongPress()
            },
            onClickLabel = clickLabel,
            onLongClickLabel = longLabel,
        )

    when (downloadState) {
        DownloadStore.STATE_QUEUED -> Box(clickModifier, contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(32.dp))
        }

        DownloadStore.STATE_DOWNLOADING -> {
            // Only this row watches byte progress, and only four times a second.
            val progress by remember(downloadId) {
                DownloadStore.entries
                    .map { entries ->
                        entries.orEmpty().firstOrNull { it.id == downloadId }?.progressFraction ?: -1f
                    }
                    .distinctUntilChanged()
                    .sample(500L)
            }.collectAsState(initial = -1f)
            Box(clickModifier, contentAlignment = Alignment.Center) {
                if (progress >= 0f) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.55f))
                }
            }
        }

        DownloadStore.STATE_PAUSED -> DownloadIconAction(
            icon = Icons.Rounded.PlayArrow,
            tint = MaterialTheme.colorScheme.primary,
            description = R.string.cd_download_resume,
            modifier = clickModifier,
        )

        DownloadStore.STATE_FAILED -> DownloadIconAction(
            icon = Icons.Rounded.Refresh,
            tint = MaterialTheme.colorScheme.error,
            description = R.string.cd_download_retry,
            modifier = clickModifier,
        )

        DownloadStore.STATE_DONE -> DownloadIconAction(
            icon = Icons.Rounded.Check,
            tint = MaterialTheme.colorScheme.primary,
            description = R.string.downloaded,
            modifier = clickModifier,
        )

        else -> DownloadIconAction(
            icon = Icons.Rounded.Download,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            description = R.string.cd_download,
            modifier = clickModifier,
        )
    }
}

@Composable
private fun DownloadIconAction(
    icon: ImageVector,
    tint: Color,
    description: Int,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SeasonWatchedAction(
    allWatched: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        TextButton(onClick = onClick) {
            Icon(
                imageVector = if (allWatched) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (allWatched) R.string.mark_season_unwatched else R.string.mark_season_watched
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeHeader(
    dubOptions: List<DubStatus>,
    selectedDub: DubStatus?,
    onSelectDub: (DubStatus) -> Unit,
    seasons: List<Int>,
    selectedSeason: Int?,
    seasonNames: List<SeasonData>,
    allEpisodes: List<Episode>,
    onSelectSeason: (Int) -> Unit,
    onLatest: () -> Unit,
    hideWatched: Boolean,
    onMarkPreviousWatched: () -> Unit,
    onDownloadSeason: () -> Unit,
    onToggleHideWatched: () -> Unit,
    seasonAction: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.episodes), Modifier.weight(1f))
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.episode_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mark_previous_watched)) },
                        onClick = {
                            onMarkPreviousWatched()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_season)) },
                        onClick = {
                            onDownloadSeason()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hide_watched)) },
                        leadingIcon = {
                            if (hideWatched) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onToggleHideWatched()
                            menuOpen = false
                        },
                    )
                }
            }
        }
        if (seasons.isNotEmpty()) {
            SeasonPicker(
                seasons = seasons,
                selected = selectedSeason,
                seasonNames = seasonNames,
                allEpisodes = allEpisodes,
                onSelect = onSelectSeason,
                onLatest = onLatest,
            )
        }
        if (dubOptions.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = dubOptions,
                    key = { it.name },
                    contentType = { "dub" },
                ) { dub ->
                    FilterChip(
                        selected = dub == selectedDub,
                        onClick = { onSelectDub(dub) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        label = { Text(dubLabel(dub)) },
                    )
                }
            }
        }
        seasonAction?.invoke()
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<Int>,
    selected: Int?,
    seasonNames: List<SeasonData>,
    allEpisodes: List<Episode>,
    onSelect: (Int) -> Unit,
    onLatest: () -> Unit,
) {
    val rowState = rememberLazyListState()
    val lastNumber = allEpisodes.maxOfOrNull { it.episode ?: 0 }?.takeIf { it > 0 }
        ?: allEpisodes.size.takeIf { it > 0 }
    val showLatest = lastNumber != null && (seasons.size > 1 || lastNumber > 12)
    val latestOffset = if (showLatest) 1 else 0
    LaunchedEffect(selected, showLatest) {
        val index = seasons.indexOf(selected)
        if (index >= 0) rowState.animateScrollToItem(index + latestOffset)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLatest && lastNumber != null) {
            item(key = "latest") {
                AssistChip(
                    onClick = onLatest,
                    shape = MaterialTheme.shapes.small,
                    label = {
                        Text(
                            stringResource(R.string.latest) + " · " +
                                stringResource(R.string.episode_badge, lastNumber)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.NewReleases,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        items(
            items = seasons,
            key = { "season-$it" },
            contentType = { "season" },
        ) { season ->
            val data = seasonNames.firstOrNull { it.season == season }
            val label = when {
                data?.name != null && data.displaySeason != null ->
                    stringResource(R.string.season_n, data.displaySeason!!) + " " + data.name
                data?.name != null -> data.name!!
                data?.displaySeason != null -> stringResource(R.string.season_n, data.displaySeason!!)
                else -> stringResource(R.string.season_n, season)
            }
            val count = allEpisodes.count { it.season == season }
            FilterChip(
                selected = season == selected,
                onClick = { onSelect(season) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                label = {
                    Text(
                        text = if (count > 0) "$label · $count" else label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun dubLabel(dub: DubStatus): String = stringResource(
    when (dub) {
        DubStatus.Dubbed -> R.string.dub
        DubStatus.Subbed -> R.string.sub
        DubStatus.None -> R.string.episodes
    }
)

/** A decided horizontal swipe (a fifth of the width) changes the season. */
@Composable
private fun Modifier.seasonSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier {
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestNext by rememberUpdatedState(onNext)
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(rtl) {
        var travel = 0f
        detectHorizontalDragGestures(
            onDragStart = { travel = 0f },
            onDragEnd = {
                val threshold = size.width / 5f
                val toNext = if (rtl) travel > threshold else travel < -threshold
                val toPrevious = if (rtl) travel < -threshold else travel > threshold
                when {
                    toNext -> {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        latestNext()
                    }

                    toPrevious -> {
                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        latestPrevious()
                    }
                }
            },
        ) { change, amount ->
            change.consume()
            travel += amount
        }
    }
}

/** Mirrors the title page: artwork, poster and title, play button, action tiles, overview. */
@Composable
private fun DetailsSkeleton() {
    SkeletonPulseProvider {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 600.dp) {
                val startWidth = (maxWidth * 0.4f).coerceAtMost(440.dp)
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(startWidth)) {
                        SkeletonHero(480.dp)
                        SkeletonSummary()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(180.dp, 28.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) {
                                SkeletonBox(
                                    modifier = Modifier.size(84.dp, 32.dp),
                                    shape = MaterialTheme.shapes.small,
                                )
                            }
                        }
                        repeat(4) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth().height(104.dp),
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SkeletonHero(540.dp)
                    SkeletonSummary()
                }
            }
        }
    }
}

@Composable
private fun SkeletonHero(height: Dp) {
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        SkeletonBox(modifier = Modifier.fillMaxSize(), shape = null)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SkeletonBox(
                modifier = Modifier.size(112.dp, 166.dp),
                shape = MaterialTheme.shapes.large,
            )
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier.size(190.dp, 30.dp),
                    shape = MaterialTheme.shapes.small,
                )
                SkeletonBox(
                    modifier = Modifier.size(130.dp, 16.dp),
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

@Composable
private fun SkeletonSummary() {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = MaterialTheme.shapes.large,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier.weight(1f).height(72.dp),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }
        repeat(3) { index ->
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.6f else 1f)
                    .height(16.dp),
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}
