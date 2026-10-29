package com.kodastream.app.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse

/** One episode in the player's playback queue, flattened across seasons. */
data class QueueEpisode(
    val season: Int?,
    val number: Int,
    val name: String?,
    /** The provider data string, or a local file URI when downloaded. */
    val data: String,
    val posterUrl: String? = null,
    /** Position in the flat ordered list. */
    val index: Int = 0,
    /** Position within this season. */
    val seasonIndex: Int = 0,
)

/**
 * Everything the player needs to walk through a series: the flat episode
 * list, seasons ascending, so "next" at the end of a season crosses into the
 * following one and "previous" at the first crosses back.
 *
 * Built once on the details page and handed to the player with the playback
 * args; when playback starts from Downloads or History instead, the player
 * rebuilds it from the details page (or, offline, from the downloaded
 * records of the same title).
 */
data class EpisodeQueue(
    val apiName: String,
    val url: String,
    val title: String,
    val posterUrl: String? = null,
    val posterHeaders: Map<String, String>? = null,
    val type: String = "series",
    /** Distinct seasons in play order; -1 stands for episodes with no season. */
    val seasons: List<Int> = emptyList(),
    val episodes: List<QueueEpisode> = emptyList(),
    /** True when the list was rebuilt from downloads while offline. */
    val offlineOnly: Boolean = false,
)

object EpisodeQueues {

    /** Groups the provider's flat list by season, keeping each season's order. */
    fun build(
        apiName: String,
        url: String,
        title: String,
        posterUrl: String?,
        posterHeaders: Map<String, String>?,
        type: String,
        episodes: List<Episode>,
    ): EpisodeQueue? {
        if (episodes.isEmpty()) return null
        val grouped = ArrayList<Pair<Int?, MutableList<Episode>>>()
        for (episode in episodes) {
            val season = episode.season
            val bucket = grouped.firstOrNull { it.first == season }?.second
            if (bucket != null) bucket.add(episode) else grouped.add(season to mutableListOf(episode))
        }
        val seasonKeys = ArrayList<Int>()
        val flat = ArrayList<QueueEpisode>()
        grouped.forEach { (season, eps) ->
            seasonKeys.add(season ?: -1)
            eps.forEachIndexed { i, ep ->
                flat.add(
                    QueueEpisode(
                        season = season,
                        number = ep.episode ?: i + 1,
                        name = ep.name?.takeIf { it.isNotBlank() },
                        data = ep.data,
                        posterUrl = ep.posterUrl,
                        index = flat.size,
                        seasonIndex = i,
                    ),
                )
            }
        }
        return EpisodeQueue(
            apiName = apiName,
            url = url,
            title = title,
            posterUrl = posterUrl,
            posterHeaders = posterHeaders,
            type = type,
            seasons = seasonKeys,
            episodes = flat,
        )
    }

    /** Rebuilds the queue from a freshly loaded details page. */
    fun fromResponse(response: LoadResponse): EpisodeQueue? {
        val episodesByDub = when (response) {
            is AnimeLoadResponse -> response.episodes.filterValues { it.isNotEmpty() }
            is TvSeriesLoadResponse ->
                if (response.episodes.isEmpty()) emptyMap() else mapOf(DubStatus.None to response.episodes)
            else -> return null
        }
        val episodes = episodesByDub.values.firstOrNull { it.isNotEmpty() } ?: return null
        return build(
            apiName = response.apiName,
            url = response.url,
            title = response.name,
            posterUrl = response.posterUrl,
            posterHeaders = response.posterHeaders,
            type = when (response) {
                is AnimeLoadResponse -> "anime"
                is TvSeriesLoadResponse -> "series"
                else -> "movie"
            },
            episodes = episodes,
        )
    }

    /** The offline fallback: whatever episodes of this title are downloaded. */
    fun fromDownloads(
        apiName: String,
        url: String,
        title: String,
        posterUrl: String?,
        posterHeaders: Map<String, String>?,
        type: String,
        downloads: List<DownloadStore.Download>,
    ): EpisodeQueue? {
        val records = downloads
            .filter {
                it.apiName == apiName && it.url == url &&
                    it.state == DownloadStore.STATE_DONE && it.fileUri != null
            }
            .sortedWith(compareBy({ it.season ?: -1 }, { it.episode ?: 0 }))
        if (records.isEmpty()) return null
        val episodes = records.mapIndexed { index, record ->
            QueueEpisode(
                season = record.season ?: -1,
                number = record.episode ?: index + 1,
                name = record.episodeTitle,
                data = record.data ?: "",
                posterUrl = posterUrl,
                index = index,
                seasonIndex = index,
            )
        }
        return EpisodeQueue(
            apiName = apiName,
            url = url,
            title = title,
            posterUrl = posterUrl,
            posterHeaders = posterHeaders,
            type = type,
            seasons = episodes.map { it.season ?: -1 }.distinct(),
            episodes = episodes,
            offlineOnly = true,
        )
    }
}
