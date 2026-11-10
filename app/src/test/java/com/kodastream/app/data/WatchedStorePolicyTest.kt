package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedStorePolicyTest {
    @Test
    fun onlyCanonicalNullNullKeyRepresentsMovie() {
        assertTrue(isMovieWatchItem(null, null))
        assertFalse(isMovieWatchItem(1, null))
        assertFalse(isMovieWatchItem(null, 1))
        assertFalse(isMovieWatchItem(0, 0))
    }

    @Test
    fun onlyKnownOnDemandTypesContributeToWatchTime() {
        assertTrue(isCountableWatchStatsType("movie"))
        assertTrue(isCountableWatchStatsType("series"))
        assertTrue(isCountableWatchStatsType("anime"))
        assertFalse(isCountableWatchStatsType("live"))
        assertFalse(isCountableWatchStatsType("other"))
    }
    @Test
    fun syntheticFallbackCoordinatesDoNotCreateEpisodeIdentity() {
        assertTrue(hasEpisodeWatchIdentity(hasQueueEpisode = true, historyEpisodeNumber = null))
        assertTrue(hasEpisodeWatchIdentity(hasQueueEpisode = false, historyEpisodeNumber = 0))
        assertFalse(hasEpisodeWatchIdentity(hasQueueEpisode = false, historyEpisodeNumber = null))
        assertFalse(hasEpisodeWatchIdentity(hasQueueEpisode = false, historyEpisodeNumber = -1))
    }

    @Test
    fun moviesCanBeMarkedWithoutEpisodeIdentity() {
        assertTrue(canMarkPlaybackAsWatched("movie", isMoviePlayback = true, hasEpisodeIdentity = false))
    }

    @Test
    fun explicitHistoryEpisodesCanBeMarkedWithoutAQueue() {
        assertTrue(canMarkPlaybackAsWatched("series", isMoviePlayback = false, hasEpisodeIdentity = true))
        assertTrue(canMarkPlaybackAsWatched("anime", isMoviePlayback = false, hasEpisodeIdentity = true))
    }

    @Test
    fun unknownSeriesEpisodeIsNotGuessedFromSyntheticCoordinates() {
        assertFalse(canMarkPlaybackAsWatched("series", isMoviePlayback = false, hasEpisodeIdentity = false))
        assertFalse(canMarkPlaybackAsWatched("anime", isMoviePlayback = false, hasEpisodeIdentity = false))
    }

    @Test
    fun watchedSeriesCountCountsUniqueTitlesNotEpisodeMarkers() {
        val keys = setOf(
            "provider|series-a|1|1",
            "provider|series-a|1|2",
            "provider|series-a|2|3",
            "provider|series-b|-1|0",
            "provider|movie|-1|-1",
        )
        assertEquals(2, watchedSeriesCount(keys))
        assertEquals(4, watchedEpisodeCount(keys))
        assertEquals(1, watchedMovieCount(keys))
    }

    @Test
    fun watchedSeriesCountIgnoresInvalidAndMovieKeys() {
        val invalid = setOf(
            "provider|movie|-1|-1",
            "provider|invalid|x|1",
            "provider|invalid|1|x",
            "|empty-title|1|2",
            "provider|invalid|-1|-1x",
        )
        assertEquals(0, watchedSeriesCount(invalid))
        assertEquals(0, watchedEpisodeCount(invalid))
        assertEquals(1, watchedMovieCount(invalid))
    }

    @Test
    fun liveAndUnsupportedItemsAreNeverMarkedAsWatched() {
        assertFalse(canMarkPlaybackAsWatched("live", isMoviePlayback = false, hasEpisodeIdentity = true))
        assertFalse(canMarkPlaybackAsWatched("other", isMoviePlayback = false, hasEpisodeIdentity = true))
    }

    @Test
    fun episodeZeroIsPreservedForSpecialsInWatchHistory() {
        assertEquals(0, episodeNumberForWatchHistory(0, 1, 2, 3))
        assertEquals(0, episodeNumberForWatchHistory(null, 0, 2, 3))
        assertEquals(0, episodeNumberForWatchHistory(null, null, 0, 3))
    }

    @Test
    fun unknownEpisodeNumbersUseTheFirstKnownFallback() {
        assertEquals(5, episodeNumberForWatchHistory(null, 5, 7, 8))
        assertEquals(7, episodeNumberForWatchHistory(null, null, 7, 8))
        assertEquals(8, episodeNumberForWatchHistory(null, null, null, 8))
        assertEquals(7, episodeNumberForWatchHistory(-1, null, 7, 8))
        assertEquals(8, episodeNumberForWatchHistory(null, -1, null, 8))
    }

    @Test
    fun legacyMovieMarkersMoveToTheCanonicalKeyOnlyForKnownMovies() {
        val keys = setOf(
            "provider|movie-a|0|0",
            "provider|series-a|0|0",
            "provider|series-a|1|2",
            "provider|movie-b|-1|-1",
        )
        val migrated = migrateLegacyMovieMarkers(keys, setOf("provider|movie-a"))
        assertEquals(
            setOf(
                "provider|movie-a|-1|-1",
                "provider|series-a|0|0",
                "provider|series-a|1|2",
                "provider|movie-b|-1|-1",
            ),
            migrated,
        )
    }

    @Test
    fun migrationKeepsTheCountsConsistent() {
        val legacy = setOf("provider|movie-a|0|0", "provider|movie-a|-1|-1", "provider|series-a|1|1")
        val migrated = migrateLegacyMovieMarkers(legacy, setOf("provider|movie-a"))
        assertEquals(1, watchedMovieCount(migrated))
        assertEquals(1, watchedEpisodeCount(migrated))
        assertEquals(1, watchedSeriesCount(migrated))
    }

    @Test
    fun migrationReturnsTheSameSetWhenNothingChanges() {
        val keys = setOf("provider|series-a|1|1", "provider|movie-a|-1|-1")
        assertTrue(migrateLegacyMovieMarkers(keys, setOf("provider|movie-a")) === keys)
        assertTrue(migrateLegacyMovieMarkers(keys, emptySet()) === keys)
    }

    @Test
    fun aFinishedMovieAndAFinishedEpisodeCountOnce() {
        // A movie finished in the player persists the canonical key...
        val movieKey = "provider|movie-a|-1|-1"
        // ...and the first episode of a series adds one episode and one series.
        val afterEpisode1 = setOf(movieKey, "provider|series-a|1|1")
        assertEquals(1, watchedMovieCount(afterEpisode1))
        assertEquals(1, watchedEpisodeCount(afterEpisode1))
        assertEquals(1, watchedSeriesCount(afterEpisode1))
        // Further episodes of the same title do not change the series count.
        val afterEpisode2 = afterEpisode1 + "provider|series-a|1|2"
        assertEquals(2, watchedEpisodeCount(afterEpisode2))
        assertEquals(1, watchedSeriesCount(afterEpisode2))
        assertEquals(1, watchedMovieCount(afterEpisode2))
    }

    @Test
    fun aMoviePersistsTheCanonicalNullKeyWhateverThePlayerReports() {
        val id = playbackWatchIdentity(
            mediaType = "movie",
            currentSeason = 0, currentEpisode = 0,
            queueSeason = null, queueEpisodeNumber = null,
            historySeason = null, historyEpisode = null,
            fallbackEpisode = 1,
        )
        assertTrue(id.isMovie)
        assertEquals(null, id.season)
        assertEquals(null, id.episode)
        assertTrue(id.canMarkWatched)
        // The details page keys a movie as null/null, which is exactly what is persisted.
        assertEquals("p|u|-1|-1", "p|u|${id.season ?: -1}|${id.episode ?: -1}")
    }

    @Test
    fun aQueuedEpisodeKeepsItsSeasonAndNumber() {
        val id = playbackWatchIdentity(
            mediaType = "series",
            currentSeason = 2, currentEpisode = 5,
            queueSeason = 2, queueEpisodeNumber = 5,
            historySeason = 1, historyEpisode = 4,
            fallbackEpisode = 9,
        )
        assertFalse(id.isMovie)
        assertEquals(2, id.season)
        assertEquals(5, id.episode)
        assertTrue(id.canMarkWatched)
    }

    @Test
    fun aSeasonlessEpisodeKeepsANullSeason() {
        val id = playbackWatchIdentity(
            mediaType = "anime",
            currentSeason = null, currentEpisode = 12,
            queueSeason = null, queueEpisodeNumber = 12,
            historySeason = null, historyEpisode = 11,
            fallbackEpisode = 12,
        )
        assertEquals(null, id.season)
        assertEquals(12, id.episode)
        assertTrue(id.canMarkWatched)
    }

    @Test
    fun withoutAQueueTheHistoryEpisodeIsTheIdentity() {
        val id = playbackWatchIdentity(
            mediaType = "series",
            currentSeason = null, currentEpisode = 0,
            queueSeason = null, queueEpisodeNumber = null,
            historySeason = 3, historyEpisode = 7,
            fallbackEpisode = 1,
        )
        assertEquals(3, id.season)
        assertEquals(0, id.episode)
        assertTrue(id.canMarkWatched)
    }

    @Test
    fun anUnknownEpisodeNeverCreatesAMarker() {
        val id = playbackWatchIdentity(
            mediaType = "series",
            currentSeason = null, currentEpisode = 0,
            queueSeason = null, queueEpisodeNumber = null,
            historySeason = null, historyEpisode = null,
            fallbackEpisode = 1,
        )
        assertEquals(null, id.episode)
        assertFalse(id.canMarkWatched)
    }
}
