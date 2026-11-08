package com.kodastream.app.ui.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.NavArgs
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.WatchStatsStore
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.data.isCountableWatchStatsType
import com.kodastream.app.data.playbackWatchIdentity
import com.kodastream.app.engine.AppBootstrap
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.DataStoreHelper

/**
 * Hosts the original CloudStream player fragment. Playback arguments come in
 * through [NavArgs] under [EXTRA_ID], because provider `data` strings are too
 * long and too fragile for an Intent extra.
 */
class PlayerActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_ID = "player_id"

        fun newIntent(context: Context, id: Int): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ID, id)
    }

    private lateinit var args: PlayerArgs
    private var watchedKey: String? = null
    private var halfWatchedKey: String? = null

    /** The player whose real play/pause state feeds the watch-time counter. */
    private var watchStatsPlayer: CS3IPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            recordProgress()
            handler.postDelayed(this, 5_000L)
        }
    }
    private val hideBars = Runnable { hideSystemBars() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        applyCutoutMode()
        hideSystemBars()
        installInsetsListener()
        CommonActivity.setActivityInstance(this)
        AppBootstrap.start(this)

        args = NavArgs.player(intent.getIntExtra(EXTRA_ID, -1)) ?: run {
            finish()
            return
        }

        if (savedInstanceState == null) {
            val (episodes, index) = episodesFor(this)
            @Suppress("DEPRECATION")
            val offlineSubs = args.offlineSubtitleUri?.let { uri ->
                listOf(
                    PlayerSubtitleHelper.getSubtitleData(
                        SubtitleFile(args.subtitle ?: "Subtitle", uri)
                    ).copy(origin = SubtitleOrigin.DOWNLOADED_FILE)
                )
            } ?: emptyList()
            supportFragmentManager.beginTransaction()
                .replace(
                    android.R.id.content,
                    GeneratorPlayer().apply {
                        arguments = GeneratorPlayer.newInstance(
                            KodaPlayerGenerator(episodes, offlineSubs),
                            index,
                        )
                    },
                )
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
        hideSystemBars()
        handler.removeCallbacks(progressTicker)
        handler.post(progressTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(hideBars)
        recordProgress()
        // A hold that never got its ACTION_UP (focus loss, rotation) must not
        // stick at 2x; the gesture helper only restores on touch end.
        restorePlaybackSpeed()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyCutoutMode()
            hideSystemBars()
        } else {
            restorePlaybackSpeed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyCutoutMode()
        hideSystemBars()
        restorePlaybackSpeed()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(hideBars)
        // Rotation keeps the counter running; leaving the player closes the open interval.
        if (isFinishing && !isChangingConfigurations) {
            WatchStatsStore.updatePlaybackState(this, false)
        } else {
            WatchStatsStore.flushActive(this)
        }
        watchStatsPlayer?.setPlaybackStateObserver(null)
        watchStatsPlayer = null
        super.onDestroy()
    }

    /** Keeps the player fully immersive; the system bars only peek transiently on a swipe. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Re-applies shortEdges after the ported player resets the cutout mode on its own exits. */
    private fun applyCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
    }

    /**
     * The ported player shows the system bars on some transitions. Hide them
     * again after a short delay, which still leaves the system's transient
     * swipe-to-peek behavior usable.
     */
    private fun installInsetsListener() {
        val content = findViewById<android.view.View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                handler.removeCallbacks(hideBars)
                handler.postDelayed(hideBars, 2_000L)
            } else {
                handler.removeCallbacks(hideBars)
            }
            insets
        }
    }

    /** Puts back the user's speed after a hold was interrupted before ACTION_UP. */
    private fun restorePlaybackSpeed() {
        val fragment = supportFragmentManager
            .findFragmentById(android.R.id.content) as? GeneratorPlayer ?: return
        val player = fragment.player
        val stored = DataStoreHelper.playBackSpeed
        if (player.getPlaybackSpeed() != stored) player.setPlaybackSpeed(stored)
    }

    /** Mirrors the fragment's position into Koda's history and per-episode prefs. */
    private fun recordProgress() {
        if (!::args.isInitialized) return
        val fragment = supportFragmentManager
            .findFragmentById(android.R.id.content) as? GeneratorPlayer ?: return
        // Viewing time is counted from the player's own play/pause state, so it is
        // updated before any of the position guards below.
        syncWatchStatistics(fragment)
        val episode = fragment.currentEpisode ?: return
        val position = fragment.player.getPosition() ?: return
        if (position < 5_000L) return
        val duration = fragment.player.getDuration()?.takeIf { it > 0 } ?: 0L
        val base = args.history ?: return

        val resumeKey = episode.resumeKey ?: base.resumeKey
        // The player hands movies the synthetic coordinates 0/0; the details page keys a
        // movie as null/null. Normalize before anything is persisted so both agree.
        val queueEpisode = args.queue?.episodes?.firstOrNull { it.index == episode.index }
            ?: args.queue?.episodes?.getOrNull(episode.index.coerceAtLeast(0))
        val identity = playbackWatchIdentity(
            mediaType = args.queue?.type ?: base.type,
            currentSeason = episode.season,
            currentEpisode = episode.episode,
            queueSeason = queueEpisode?.season,
            queueEpisodeNumber = queueEpisode?.number,
            historySeason = base.season,
            historyEpisode = base.episode,
            fallbackEpisode = episode.index.coerceAtLeast(0) + 1,
        )
        val entry = base.copy(
            season = identity.season,
            episode = identity.episode,
            episodeTitle = episode.name,
            data = episode.data,
            resumeKey = resumeKey,
            positionMs = position,
            durationMs = duration,
        )
        // "Pause history" stops new History entries and resume positions; what is
        // already saved stays, and watched markers below are still written.
        if (!AppPrefs.pauseHistory(this)) {
            val changedEpisode = HistoryStore.current(this)
                .firstOrNull { it.key == entry.key }?.resumeKey != resumeKey
            if (changedEpisode) HistoryStore.upsert(this, entry)
            else HistoryStore.updateProgress(this, entry.key, position, duration)
            AppPrefs.setPosition(this, resumeKey, position)
            if (duration > 0) AppPrefs.setDuration(this, resumeKey, duration)
        }

        // Halfway marks the episode itself. History stays untouched on purpose:
        // marking the whole title complete would hide "Continue watching". A movie is
        // only marked once it is really finished (below).
        if (identity.canMarkWatched && !identity.isMovie &&
            duration > 0 && position >= duration / 2 && halfWatchedKey != resumeKey
        ) {
            halfWatchedKey = resumeKey
            WatchedStore.setAll(
                this,
                listOf(WatchedStore.keyOf(entry.apiName, entry.url, identity.season, identity.episode)),
                true,
            )
        }

        if (identity.canMarkWatched && duration > 0 && position >= duration * 0.9 && watchedKey != resumeKey) {
            watchedKey = resumeKey
            WatchedStore.setWatched(
                context = this,
                apiName = entry.apiName,
                url = entry.url,
                season = identity.season,
                episode = identity.episode,
                resumeKey = resumeKey,
                watched = true,
            )
        }
    }

    /** Binds the real ExoPlayer play/pause state to the wall-clock watch-time counter. */
    private fun syncWatchStatistics(fragment: GeneratorPlayer) {
        val mediaType = args.queue?.type ?: args.history?.type.orEmpty()
        val player = fragment.player as? CS3IPlayer
        val enabled = AppPrefs.watchStatisticsEnabled(this) && isCountableWatchStatsType(mediaType)
        if (!enabled || player == null) {
            watchStatsPlayer?.setPlaybackStateObserver(null)
            watchStatsPlayer = null
            WatchStatsStore.updatePlaybackState(this, false)
            return
        }

        if (watchStatsPlayer !== player) {
            watchStatsPlayer?.setPlaybackStateObserver(null)
            watchStatsPlayer = player
            player.setPlaybackStateObserver { isPlaying ->
                val stillEnabled = ::args.isInitialized &&
                    AppPrefs.watchStatisticsEnabled(this) &&
                    isCountableWatchStatsType(args.queue?.type ?: args.history?.type.orEmpty())
                WatchStatsStore.updatePlaybackState(this, stillEnabled && isPlaying)
            }
        } else {
            // Reconcile after an activity recreation or a missed callback.
            WatchStatsStore.updatePlaybackState(this, player.getIsPlaying())
        }
        // Persist the open interval every few seconds without stopping the clock.
        WatchStatsStore.flushActive(this)
    }

    /** Turns Koda's playback arguments into the episode list the original player expects. */
    private fun episodesFor(context: Context): Pair<List<ResultEpisode>, Int> {
        val queue = args.queue
        val template = args.history
        val tvType = when (queue?.type ?: template?.type) {
            "anime" -> TvType.Anime
            "series" -> TvType.TvSeries
            else -> TvType.Movie
        }

        val episodes = if (queue != null && queue.episodes.isNotEmpty()) {
            queue.episodes.map { ep ->
                val resumeKey = if (ep.data.startsWith("content://") || ep.data.startsWith("file://")) {
                    "local|" + DownloadStore.idOf(queue.apiName, queue.url, ep.season, ep.number)
                } else {
                    queue.apiName + "|" + ep.data
                }
                ResultEpisode(
                    headerName = queue.title,
                    name = ep.name,
                    poster = ep.posterUrl ?: queue.posterUrl,
                    episode = ep.number,
                    seasonIndex = ep.seasonIndex,
                    season = ep.season,
                    data = ep.data,
                    apiName = queue.apiName,
                    id = resumeKey.hashCode(),
                    index = ep.index,
                    position = 0L,
                    duration = 0L,
                    score = null,
                    description = null,
                    isFiller = false,
                    tvType = tvType,
                    parentId = queue.url.hashCode(),
                    videoWatchState = VideoWatchState.None,
                    totalEpisodeIndex = ep.index,
                    resumeKey = resumeKey,
                )
            }
        } else {
            listOf(
                ResultEpisode(
                    headerName = args.title,
                    name = template?.episodeTitle ?: args.subtitle,
                    poster = template?.posterUrl,
                    episode = template?.episode ?: 0,
                    // Without a queue the real season is unknown; null is safer than "Season 0".
                    seasonIndex = null,
                    season = template?.season,
                    data = args.data,
                    apiName = args.apiName,
                    id = args.resumeKey.hashCode(),
                    index = 0,
                    position = 0L,
                    duration = 0L,
                    score = null,
                    description = null,
                    isFiller = false,
                    tvType = tvType,
                    parentId = (template?.url ?: args.resumeKey).hashCode(),
                    videoWatchState = VideoWatchState.None,
                    resumeKey = args.resumeKey,
                ),
            )
        }

        var index = episodes.indexOfFirst { it.data == args.data }
        if (index < 0 && template != null) {
            index = episodes.indexOfFirst {
                it.season == template.season && it.episode == template.episode
            }
        }
        if (index < 0) index = 0

        // Seed the original player's resume store from Koda's saved history and
        // per-episode positions, so playback starts where it stopped.
        val known = template?.let { h ->
            HistoryStore.current(context).firstOrNull { it.key == h.key }
        }
        episodes.forEach { episode ->
            val duration = known?.takeIf { it.resumeKey == episode.resumeKey }?.durationMs ?: 0L
            val position = AppPrefs.position(context, episode.resumeKey ?: "")
            if (duration > 30_000L && position > 0L) {
                DataStoreHelper.setViewPos(episode.id, position, duration)
            }
        }

        return episodes to index
    }
}
