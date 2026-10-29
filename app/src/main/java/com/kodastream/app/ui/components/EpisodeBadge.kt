package com.kodastream.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R

/** What an entry's season/episode labels should show, if anything. */
sealed interface EpisodeBadge {
    data class SeasonEpisode(val season: Int, val episode: Int) : EpisodeBadge
    data class EpisodeOnly(val episode: Int) : EpisodeBadge
}

/**
 * Providers use 0 (and sometimes -1) for "no season/episode", so only positive
 * numbers count. A season without an episode is not useful on a card, and a
 * movie gets no badge at all.
 */
fun episodeBadge(season: Int?, episode: Int?): EpisodeBadge? {
    val validSeason = season?.takeIf { it > 0 }
    val validEpisode = episode?.takeIf { it > 0 }
    return when {
        validSeason != null && validEpisode != null ->
            EpisodeBadge.SeasonEpisode(validSeason, validEpisode)
        validEpisode != null -> EpisodeBadge.EpisodeOnly(validEpisode)
        else -> null
    }
}

@Composable
fun episodeBadgeLabel(badge: EpisodeBadge): String = when (badge) {
    is EpisodeBadge.SeasonEpisode ->
        stringResource(R.string.season_episode_badge, badge.season, badge.episode)
    is EpisodeBadge.EpisodeOnly -> stringResource(R.string.episode_badge, badge.episode)
}

@Composable
fun episodeBadgeDescription(badge: EpisodeBadge): String = when (badge) {
    is EpisodeBadge.SeasonEpisode ->
        stringResource(R.string.cd_season_episode, badge.season, badge.episode)
    is EpisodeBadge.EpisodeOnly -> stringResource(R.string.episode_n, badge.episode)
}
