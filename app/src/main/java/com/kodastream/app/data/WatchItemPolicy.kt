package com.kodastream.app.data

/**
 * Movie watched markers use the canonical null/null key. An episode marker
 * must never be interpreted as completion of the whole title.
 */
internal fun isMovieWatchItem(season: Int?, episode: Int?): Boolean = season == null && episode == null

/**
 * Resolve the episode coordinate used by history/progress tracking.
 * Episode zero is valid for specials in some providers. Negative values are
 * internal sentinels and are ignored; movie playback is handled separately.
 */
internal fun episodeNumberForWatchHistory(
    currentEpisode: Int?,
    queuedEpisode: Int?,
    historyEpisode: Int?,
    fallback: Int,
): Int = currentEpisode?.takeIf { it >= 0 }
    ?: queuedEpisode?.takeIf { it >= 0 }
    ?: historyEpisode?.takeIf { it >= 0 }
    ?: fallback.coerceAtLeast(0)

/** Only supported on-demand work types contribute to content watch-time totals. */
internal fun isCountableWatchStatsType(type: String): Boolean =
    type == "movie" || type == "series" || type == "anime"

/** True only when a queue or persisted history supplies a real episode identity. */
internal fun hasEpisodeWatchIdentity(hasQueueEpisode: Boolean, historyEpisodeNumber: Int?): Boolean =
    hasQueueEpisode || (historyEpisodeNumber != null && historyEpisodeNumber >= 0)

/**
 * Decide whether a completed playback has enough identity to create a watched marker.
 * Movies have one title-level marker; episodes require a real queue coordinate or
 * an explicit episode number from persisted history. A synthetic player fallback
 * (episode 0/1) must never mark an unknown series episode as watched.
 */
internal fun canMarkPlaybackAsWatched(
    mediaType: String,
    isMoviePlayback: Boolean,
    hasEpisodeIdentity: Boolean,
): Boolean = when (mediaType) {
    "movie" -> isMoviePlayback
    "series", "anime" -> !isMoviePlayback && hasEpisodeIdentity
    else -> false
}

/** The coordinates a finished playback persists under, and whether it may be marked watched. */
internal data class PlaybackWatchIdentity(
    val isMovie: Boolean,
    val season: Int?,
    val episode: Int?,
    val canMarkWatched: Boolean,
)

/**
 * Normalizes what the player knows about the current item into what History and the
 * watched markers must store. The player hands movies the synthetic coordinates 0/0,
 * but the details page keys a movie as null/null (`-1|-1`); a series episode without a
 * season stays null for the same reason. An episode coordinate is only produced when a
 * queue entry or the persisted history supplies a real one.
 */
internal fun playbackWatchIdentity(
    mediaType: String,
    currentSeason: Int?,
    currentEpisode: Int?,
    queueSeason: Int?,
    queueEpisodeNumber: Int?,
    historySeason: Int?,
    historyEpisode: Int?,
    fallbackEpisode: Int,
): PlaybackWatchIdentity {
    val isMovie = mediaType == "movie"
    val hasIdentity = hasEpisodeWatchIdentity(
        hasQueueEpisode = queueEpisodeNumber != null && queueEpisodeNumber >= 0,
        historyEpisodeNumber = historyEpisode,
    )
    val season = if (isMovie) null else currentSeason ?: queueSeason ?: historySeason
    val episode = if (isMovie || !hasIdentity) null else episodeNumberForWatchHistory(
        currentEpisode = currentEpisode?.takeIf { it >= 0 },
        queuedEpisode = queueEpisodeNumber,
        historyEpisode = historyEpisode,
        fallback = fallbackEpisode,
    )
    return PlaybackWatchIdentity(
        isMovie = isMovie,
        season = season,
        episode = episode,
        canMarkWatched = canMarkPlaybackAsWatched(mediaType, isMovie, hasIdentity),
    )
}

/** Parsed tail of a watched marker; everything before the final two pipes is title identity. */
private data class WatchedKeyParts(
    val titleKey: String,
    val season: Int,
    val episode: Int,
)

private fun parseWatchedKey(key: String): WatchedKeyParts? {
    val episodeSeparator = key.lastIndexOf('|')
    if (episodeSeparator <= 0) return null
    val seasonSeparator = key.lastIndexOf('|', episodeSeparator - 1)
    if (seasonSeparator <= 0) return null
    val season = key.substring(seasonSeparator + 1, episodeSeparator).toIntOrNull() ?: return null
    val episode = key.substring(episodeSeparator + 1).toIntOrNull() ?: return null
    if (season < -1 || episode < -1) return null
    val titleKey = key.substring(0, seasonSeparator).takeIf { it.isNotBlank() } ?: return null
    // "provider|url": both the source name and the title url must be present, otherwise
    // the marker cannot be tied to one title of one source and must not be counted.
    val apiPart = titleKey.substringBefore('|', missingDelimiterValue = "")
    val urlPart = titleKey.substringAfter('|', missingDelimiterValue = "")
    if (apiPart.isBlank() || urlPart.isBlank()) return null
    return WatchedKeyParts(titleKey, season, episode)
}

/** Stable title identity encoded in a watched episode key; movie/title markers are excluded. */
internal fun seriesTitleKeyFromWatchedEpisodeKey(key: String): String? =
    parseWatchedKey(key)?.takeIf { it.episode >= 0 }?.titleKey

/** Count only canonical movie markers, not malformed or partial watched keys. */
internal fun watchedMovieCount(watchedKeys: Set<String>): Int =
    watchedKeys.count { key ->
        parseWatchedKey(key)?.let { it.season == -1 && it.episode == -1 } == true
    }

/** Count only episode markers with a valid non-negative episode coordinate. */
internal fun watchedEpisodeCount(watchedKeys: Set<String>): Int =
    watchedKeys.count { key -> parseWatchedKey(key)?.episode?.let { it >= 0 } == true }

/** Number of distinct series/anime titles represented by watched episode markers. */
internal fun watchedSeriesCount(watchedKeys: Set<String>): Int =
    watchedKeys.mapNotNull(::seriesTitleKeyFromWatchedEpisodeKey).toSet().size

/**
 * Before 0.12.0 a movie finished in the player was recorded under the synthetic
 * coordinates 0/0 (`title|0|0`), while the details page uses the canonical movie key
 * (`title|-1|-1`). Rewrites those legacy markers to the canonical key when the title is
 * known to be a movie, so they are neither counted as an episode nor shown as unwatched.
 * Returns the same set when there is nothing to migrate.
 */
internal fun migrateLegacyMovieMarkers(
    watchedKeys: Set<String>,
    movieTitleKeys: Set<String>,
): Set<String> {
    if (movieTitleKeys.isEmpty()) return watchedKeys
    var changed = false
    val result = LinkedHashSet<String>(watchedKeys.size)
    for (key in watchedKeys) {
        val parts = parseWatchedKey(key)
        if (parts != null && parts.season == 0 && parts.episode == 0 && parts.titleKey in movieTitleKeys) {
            result.add("${parts.titleKey}|-1|-1")
            changed = true
        } else {
            result.add(key)
        }
    }
    return if (changed) result else watchedKeys
}
