package com.kodastream.app.engine

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TorrentSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// The library's search-response constructors are deprecated in favour of suspend builders
// that need a provider instance; plain data objects are all these tests need.
@Suppress("DEPRECATION_ERROR")
class SourceStampingTest {
    @Test
    fun aBlankDeclaredSourceTakesTheProviderName() {
        assertEquals("Provider", sourceNameFor("", "Provider"))
        assertEquals("Provider", sourceNameFor("   ", "Provider"))
        assertEquals("Provider", sourceNameFor(null, "Provider"))
    }

    @Test
    fun aDeclaredSourceIsLeftAlone() {
        // A metaprovider points at sub-providers; they must keep their own name.
        assertEquals("SubProvider", sourceNameFor("SubProvider", "MetaProvider"))
    }

    @Test
    fun resultsWithoutAUrlAreDropped() {
        assertFalse(keepResult(""))
        assertFalse(keepResult("  "))
        assertFalse(keepResult(null))
        assertTrue(keepResult("https://example.com/title/1"))
    }

    @Test
    fun aResultAlreadyCarryingTheSourceIsReturnedUnchanged() {
        val item = MovieSearchResponse(name = "Film", url = "u", apiName = "Provider", type = TvType.Movie)
        assertSame(item, withSource(item, "Provider"))
    }

    @Test
    fun aBlankSourceIsReplacedByACopyOfTheSameConcreteType() {
        val anime = withSource(AnimeSearchResponse("A", "u1", "", TvType.Anime, year = 2020), "Provider")
        assertEquals("Provider", anime.apiName)
        assertEquals(2020, anime.year)

        assertEquals("Provider", withSource(MovieSearchResponse("M", "u2", "", TvType.Movie, year = 1999), "Provider").apiName)
        assertEquals("Provider", withSource(TvSeriesSearchResponse("T", "u3", "", TvType.TvSeries), "Provider").apiName)
        assertEquals("Provider", withSource(LiveSearchResponse("L", "u4", "", TvType.Live), "Provider").apiName)
        assertEquals("Provider", withSource(TorrentSearchResponse("X", "u5", "", TvType.Torrent, posterUrl = null), "Provider").apiName)
    }

    @Test
    fun stampingDropsUnopenableResultsAndKeepsDeclaredSources() {
        val stamped = stampResults(
            providerName = "Meta",
            items = listOf(
                MovieSearchResponse("Blank source", "u1", "", TvType.Movie),
                MovieSearchResponse("Own source", "u2", "Sub", TvType.Movie),
                MovieSearchResponse("No url", "", "", TvType.Movie),
            ),
        )
        assertEquals(listOf("Blank source" to "Meta", "Own source" to "Sub"), stamped.map { it.name to it.apiName })
    }
}
