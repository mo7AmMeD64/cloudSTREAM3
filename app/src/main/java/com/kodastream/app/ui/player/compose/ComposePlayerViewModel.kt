package com.kodastream.app.ui.player.compose

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.CurrentTracks
import com.lagradost.cloudstream3.ui.player.EmbeddedSubtitlesFetchedEvent
import com.lagradost.cloudstream3.ui.player.EpisodeSeekEvent
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.ui.player.IPlayer
import com.lagradost.cloudstream3.ui.player.LOADTYPE_ALL
import com.lagradost.cloudstream3.ui.player.PlayerAttachedEvent
import com.lagradost.cloudstream3.ui.player.PlayerEvent
import com.lagradost.cloudstream3.ui.player.PositionEvent
import com.lagradost.cloudstream3.ui.player.StatusEvent
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitlesUpdatedEvent
import com.lagradost.cloudstream3.ui.player.TracksChangedEvent
import com.lagradost.cloudstream3.ui.player.VideoEndedEvent
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.kodastream.app.ui.player.KodaPlayerGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state + engine bridge for the Compose player. The engine itself is untouched. */
class ComposePlayerViewModel(
    private val makeGenerator: (List<ResultEpisode>) -> KodaPlayerGenerator,
    private val episodes: List<ResultEpisode>,
    private val initialIndex: Int,
    private val title: String,
    private val initialSubtitle: SubtitleData?,
    private val startPosition: Long?,
) : ViewModel() {

    val player: IPlayer = CS3IPlayer()

    private val _position = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _status = MutableStateFlow(CSPlayerLoading.IsBuffering)
    private val _speed = MutableStateFlow(player.getPlaybackSpeed())
    private val _tracks = MutableStateFlow<CurrentTracks?>(null)
    private val _exoPlayer = MutableStateFlow<androidx.media3.common.Player?>(null)
    private val _subtitles = MutableStateFlow<List<SubtitleData>>(emptyList())
    private val _sources = MutableStateFlow<List<ExtractorLink>>(emptyList())
    private val _selectedSource = MutableStateFlow(0)
    private var appContext: Context? = null
    private val _error = MutableStateFlow<String?>(null)
    private val _controlsVisible = MutableStateFlow(true)
    private val _index = MutableStateFlow(initialIndex)
    private val _events = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)

    val position: StateFlow<Long> = _position.asStateFlow()
    val duration: StateFlow<Long> = _duration.asStateFlow()
    val status: StateFlow<CSPlayerLoading> = _status.asStateFlow()
    val speed: StateFlow<Float> = _speed.asStateFlow()
    val tracks: StateFlow<CurrentTracks?> = _tracks.asStateFlow()
    val exoPlayer: StateFlow<androidx.media3.common.Player?> = _exoPlayer.asStateFlow()
    val subtitles: StateFlow<List<SubtitleData>> = _subtitles.asStateFlow()
    val sources: StateFlow<List<ExtractorLink>> = _sources.asStateFlow()
    val selectedSource: StateFlow<Int> = _selectedSource.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()
    val index: StateFlow<Int> = _index.asStateFlow()
    val effects = _events.asSharedFlow()

    val currentEpisode: ResultEpisode? get() = episodes.getOrNull(_index.value)
    val episodeCount: Int get() = episodes.size
    fun episodeTitle(i: Int): String = episodes.getOrNull(i)?.name ?: "Episode ${i + 1}"
    val mediaTitle: String get() = currentEpisode?.name ?: title

    sealed interface UiEffect {
        data object Close : UiEffect
        data object Reload : UiEffect
        data class Message(val text: String) : UiEffect
    }

    init {
        player.initCallbacks(::onEvent)
    }

    private fun onEvent(event: PlayerEvent) {
        when (event) {
            is PositionEvent -> {
                _position.value = event.toMs
                _duration.value = event.durationMs
            }
            is StatusEvent -> _status.value = event.isPlaying
            is PlayerAttachedEvent -> _exoPlayer.value = event.player as? androidx.media3.common.Player
            is TracksChangedEvent -> _tracks.value = player.getVideoTracks()
            is EmbeddedSubtitlesFetchedEvent -> _subtitles.value = event.tracks
            is SubtitlesUpdatedEvent -> _tracks.value = player.getVideoTracks()
            is ErrorEvent -> _error.value = event.error.message ?: "Playback error"
            is VideoEndedEvent -> _events.tryEmit(UiEffect.Close)
            is EpisodeSeekEvent -> when (event.offset) {
                -1 -> switchEpisode(_index.value - 1)
                1 -> switchEpisode(_index.value + 1)
            }
            else -> Unit
        }
    }

    fun load(context: Context) {
        appContext = context.applicationContext
        viewModelScope.launch {
            val i = _index.value
            val generator = makeGenerator(episodes)
            val links = mutableListOf<Pair<ExtractorLink?, ExtractorUri?>>()
            val subs = linkedSetOf<SubtitleData>()
            initialSubtitle?.let(subs::add)
            runCatching {
                generator.generateLinks(false, LOADTYPE_ALL, { links.add(it) }, { subs.add(it) }, i, false)
            }.onFailure {
                _error.value = it.message ?: "Failed to load source"
                return@launch
            }
            val (link, uri) = links.firstOrNull() ?: run {
                _error.value = "No playable source found"
                return@launch
            }
            _subtitles.value = subs.toList()
            _sources.value = links.mapNotNull { it.first }
            _selectedSource.value = 0
            player.loadPlayer(
                context,
                false,
                link,
                uri,
                startPosition = if (i == initialIndex) startPosition else 0L,
                subs,
                (subs.firstOrNull { it == initialSubtitle }) ?: subs.firstOrNull(),
                true,
                true,
            )
            _speed.value = player.getPlaybackSpeed()
            _tracks.value = player.getVideoTracks()
        }
    }

    fun switchEpisode(nextIndex: Int) {
        if (nextIndex !in episodes.indices) {
            _events.tryEmit(UiEffect.Close)
            return
        }
        _index.value = nextIndex
        _position.value = 0L
        _duration.value = 0L
        player.release()
        _events.tryEmit(UiEffect.Reload)
    }

    fun selectSource(index: Int) {
        val link = _sources.value.getOrNull(index) ?: return
        val ctx = appContext ?: return
        _selectedSource.value = index
        player.loadPlayer(
            ctx, true, link, null, null, _subtitles.value.toSet(),
            player.getCurrentPreferredSubtitle(), true, true,
        )
    }

    fun selectSubtitle(subtitle: SubtitleData?) {
        player.setPreferredSubtitles(subtitle)
        _tracks.value = player.getVideoTracks()
    }

    fun selectAudio(language: String?, id: String?, formatIndex: Int?) {
        player.setPreferredAudioTrack(language, id, formatIndex)
        _tracks.value = player.getVideoTracks()
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        player.setSubtitleOffset(player.getSubtitleOffset() + deltaMs)
    }

    fun playPause() = player.handleEvent(com.lagradost.cloudstream3.ui.player.CSPlayerEvent.PlayPauseToggle)
    fun seekBy(deltaMs: Long) = player.seekTime((player.getPosition() ?: 0L) + deltaMs)
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    fun setControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        player.releaseCallbacks()
        player.release()
        super.onCleared()
    }
}
