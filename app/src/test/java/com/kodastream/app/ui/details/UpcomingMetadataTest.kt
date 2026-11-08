package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The library's response constructors are deprecated in favour of suspend builders that
// need a provider instance; a plain data object is all these tests need.
@Suppress("DEPRECATION_ERROR")
class UpcomingMetadataTest {
    private val now = 1_800_000_000L
    private val nowMillis = now * 1000L

    private fun movie(comingSoon: Boolean = false, type: TvType = TvType.Movie) = MovieLoadResponse(
        name = "Movie", url = "movie-url", apiName = "api", type = type, dataUrl = "data",
        comingSoon = comingSoon,
    )

    private fun series(
        episodes: List<Episode>,
        comingSoon: Boolean = false,
    ) = TvSeriesLoadResponse(
        name = "Series", url = "series-url", apiName = "api", type = TvType.TvSeries,
        episodes = episodes, comingSoon = comingSoon,
    )

    private fun anime(
        episodes: Map<DubStatus, List<Episode>> = emptyMap(),
        comingSoon: Boolean = false,
        nextAiring: NextAiring? = null,
    ) = AnimeLoadResponse(
        name = "Anime", url = "anime-url", apiName = "api", type = TvType.Anime,
        episodes = episodes.toMutableMap(), comingSoon = comingSoon, nextAiring = nextAiring,
    )

    private fun episode(data: String, date: Long? = null) = Episode(data = data, date = date)

    @Test
    fun aMovieIsUpcomingOnlyWhenTheProviderSaysSo() {
        assertTrue(hasExplicitUpcomingMetadata(movie(comingSoon = true), now))
        assertFalse(hasExplicitUpcomingMetadata(movie(), now))
    }

    @Test
    fun liveStreamsAreNeverUpcoming() {
        assertFalse(hasExplicitUpcomingMetadata(movie(comingSoon = true, type = TvType.Live), now))
    }

    @Test
    fun aSeriesIsUpcomingOnlyWhenEveryEpisodeAirsInTheFuture() {
        val future = episode("a", nowMillis + 1_000)
        val past = episode("b", nowMillis - 1_000)
        assertTrue(hasExplicitUpcomingMetadata(series(listOf(future, future)), now))
        assertFalse(hasExplicitUpcomingMetadata(series(listOf(past, future)), now))
        assertFalse(hasExplicitUpcomingMetadata(series(listOf(future, episode("c"))), now))
    }

    @Test
    fun aShowLevelFlagNeverOverridesAlreadyAiredEpisodes() {
        val past = episode("b", nowMillis - 1_000)
        assertFalse(hasExplicitUpcomingMetadata(series(listOf(past), comingSoon = true), now))
        val released = anime(episodes = mapOf(DubStatus.Subbed to listOf(past)), comingSoon = true)
        assertFalse(hasExplicitUpcomingMetadata(released, now))
    }

    @Test
    fun aFutureNextAiringTimestampIsEvidenceWhenThereAreNoEpisodeRows() {
        assertTrue(hasExplicitUpcomingMetadata(anime(nextAiring = NextAiring(1, now + 60)), now))
        assertFalse(hasExplicitUpcomingMetadata(anime(nextAiring = NextAiring(1, now - 60)), now))
    }
}
