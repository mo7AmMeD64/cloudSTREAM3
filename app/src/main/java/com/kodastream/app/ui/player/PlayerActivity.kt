package com.kodastream.app.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.data.HistoryStore
import com.kodastream.app.data.NavArgs
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.data.WatchedStore
import com.kodastream.app.engine.AppBootstrap
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper
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

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            recordProgress()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    )
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
        handler.removeCallbacks(progressTicker)
        handler.post(progressTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(progressTicker)
        recordProgress()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTicker)
        super.onDestroy()
    }

    /** Mirrors the fragment's position into Koda's history and per-episode prefs. */
    private fun recordProgress() {
        if (!::args.isInitialized) return
        val fragment = supportFragmentManager
            .findFragmentById(android.R.id.content) as? GeneratorPlayer ?: return
        val episode = fragment.currentEpisode ?: return
        val position = fragment.player.getPosition() ?: return
        if (position < 5_000L) return
        val duration = fragment.player.getDuration()?.takeIf { it > 0 } ?: 0L
        val base = args.history ?: return

        val resumeKey = episode.resumeKey ?: base.resumeKey
        val entry = base.copy(
            season = episode.season,
            episode = episode.episode,
            episodeTitle = episode.name,
            data = episode.data,
            resumeKey = resumeKey,
            positionMs = position,
            durationMs = duration,
        )
        val changedEpisode = HistoryStore.current(this)
            .firstOrNull { it.key == entry.key }?.resumeKey != resumeKey
        if (changedEpisode) HistoryStore.upsert(this, entry)
        else HistoryStore.updateProgress(this, entry.key, position, duration)
        AppPrefs.setPosition(this, resumeKey, position)

        if (duration > 0 && position >= duration * 0.9 && watchedKey != resumeKey) {
            watchedKey = resumeKey
            WatchedStore.setWatched(
                context = this,
                apiName = entry.apiName,
                url = entry.url,
                season = episode.season,
                episode = episode.episode,
                resumeKey = resumeKey,
                watched = true,
            )
        }
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
                    seasonIndex = 0,
                    season = template?.season ?: 0,
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
