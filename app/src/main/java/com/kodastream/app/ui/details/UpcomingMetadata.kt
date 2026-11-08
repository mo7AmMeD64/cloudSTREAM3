package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType

/**
 * Only provider-declared upcoming state or a future airing timestamp is evidence that
 * a work has not been released yet. Used to confirm a "Coming soon" label on a work
 * in the "More" rail.
 */
internal fun hasExplicitUpcomingMetadata(response: LoadResponse, nowUnixSeconds: Long): Boolean {
    if (response.type == TvType.Live) return false

    val episodeRows = when (response) {
        is AnimeLoadResponse -> response.episodes.values.flatten()
        is TvSeriesLoadResponse -> response.episodes
        else -> emptyList()
    }

    // A show-level flag must never turn an already released series into a wholly
    // upcoming work. For a series with rows, every listed episode must carry a
    // future date. A mixture of past/future/unknown dates is an airing series,
    // not a pre-release title.
    if (episodeRows.isNotEmpty()) {
        val nowMillis = nowUnixSeconds * 1000L
        return episodeRows.all { episode -> episode.date?.let { it > nowMillis } == true }
    }

    // Movies and metadata-only series have no episode rows to contradict the
    // provider's explicit declaration. A future next-airing timestamp is also
    // acceptable evidence when the provider has not emitted the episodes yet.
    val hasFutureNextAiring =
        (response as? EpisodeResponse)?.nextAiring?.unixTime?.let { it > nowUnixSeconds } == true
    return response.comingSoon || hasFutureNextAiring
}
