package com.kodastream.app.ui.home.hero

import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HeroModelTest {

    @Test
    fun `hero style parses every stored value and falls back to carousel`() {
        assertEquals(HeroStyle.CAROUSEL, HeroStyle.parse(null))
        assertEquals(HeroStyle.CAROUSEL, HeroStyle.parse("carousel"))
        assertEquals(HeroStyle.CINEMATIC, HeroStyle.parse("cinematic"))
        assertEquals(HeroStyle.OFF, HeroStyle.parse("off"))
        assertEquals(HeroStyle.CAROUSEL, HeroStyle.parse("something else"))
    }

    @Test
    fun `search response maps to the hero item shape`() {
        val headers = mapOf("Referer" to "https://example.com")
        val response = FakeSearchResponse(
            name = "A Show",
            url = "https://example.com/show",
            type = TvType.TvSeries,
            posterUrl = "https://example.com/poster.jpg",
            posterHeaders = headers,
        )

        val item = response.toHeroItem()

        assertEquals("https://example.com/show", item.id)
        assertEquals("series", item.type)
        assertEquals("A Show", item.name)
        assertEquals("https://example.com/poster.jpg", item.poster)
        assertEquals(headers, item.posterHeaders)
        assertSame(response, item.source)
        assertEquals("series:https://example.com/show", item.stableKey())
    }

    @Test
    fun `cloudstream types map to stable hero types`() {
        assertEquals("movie", heroItemType(TvType.Movie))
        assertEquals("movie", heroItemType(TvType.AnimeMovie))
        assertEquals("series", heroItemType(TvType.TvSeries))
        assertEquals("series", heroItemType(TvType.Anime))
        assertEquals("documentary", heroItemType(TvType.Documentary))
        assertEquals("movie", heroItemType(null))
    }

    @Test
    fun `full meta fills hero fields over catalog values`() {
        val item = HeroItem(
            id = "tt1",
            type = "series",
            name = "Show",
            imdbRating = "7.1",
            genres = listOf("Drama"),
            runtime = "52",
            status = "continuing",
        )

        val merged = item.withHeroMeta(
            HomeHeroMeta(
                banner = "banner",
                logo = "logo",
                genres = listOf("Crime"),
                runtime = "58",
                releaseInfo = "2020",
                status = "ended",
                ageRating = "TV-MA",
                imdbRating = "8.4",
            ),
        )

        assertEquals("banner", merged.banner)
        assertEquals("logo", merged.logo)
        assertEquals("8.4", merged.imdbRating)
        assertEquals(listOf("Crime"), merged.genres)
        assertEquals("58", merged.runtime)
        assertEquals("2020", merged.releaseInfo)
        assertEquals("ended", merged.status)
        assertEquals("TV-MA", merged.ageRating)
    }

    @Test
    fun `catalog values stay when full meta has gaps`() {
        val item = HeroItem(
            id = "tt1",
            type = "series",
            name = "Show",
            imdbRating = "7.1",
            genres = listOf("Drama"),
            runtime = "52",
            status = "continuing",
        )

        val merged = item.withHeroMeta(
            HomeHeroMeta(
                runtime = "",
                imdbRating = "",
                genres = emptyList(),
            ),
        )

        assertEquals("7.1", merged.imdbRating)
        assertEquals(listOf("Drama"), merged.genres)
        assertEquals("52", merged.runtime)
        assertEquals("continuing", merged.status)
        assertNull(merged.ageRating)
    }

    @Test
    fun `plain load values map to hero meta and merge into a poster-only item`() {
        val meta = heroMetaFromValues(
            banner = "https://example.com/banner.jpg",
            logo = " ",
            genres = listOf("Crime", ""),
            duration = 58,
            year = 2020,
            status = "ended",
            ageRating = "TV-MA",
            score = 8.44,
        )

        assertEquals("https://example.com/banner.jpg", meta.banner)
        assertNull(meta.logo)
        assertEquals(listOf("Crime"), meta.genres)
        assertEquals("58", meta.runtime)
        assertEquals("2020", meta.releaseInfo)
        assertEquals("ended", meta.status)
        assertEquals("TV-MA", meta.ageRating)
        assertEquals("8.4", meta.imdbRating)

        val merged = HeroItem(id = "tt1", type = "series", name = "Show").withHeroMeta(meta)
        assertEquals("https://example.com/banner.jpg", merged.banner)
        assertEquals("8.4", merged.imdbRating)
    }

    @Test
    fun `empty or zero load values leave the poster-only look`() {
        val meta = heroMetaFromValues(duration = 0, score = 0.0)

        assertNull(meta.runtime)
        assertNull(meta.imdbRating)
        assertNull(meta.banner)
        assertEquals(emptyList<String>(), meta.genres)
    }

    @Test
    fun `cache keeps only the newest entries`() {
        val cache = HeroMetaCache(maxEntries = 3)
        cache.put("a", HomeHeroMeta(imdbRating = "1"))
        cache.put("b", HomeHeroMeta(imdbRating = "2"))
        cache.put("c", HomeHeroMeta(imdbRating = "3"))
        assertEquals(3, cache.size)

        cache.put("d", HomeHeroMeta(imdbRating = "4"))
        assertEquals(3, cache.size)
        assertNull(cache.get("a"))
        assertEquals("4", cache.get("d")?.imdbRating)

        cache.put("b", HomeHeroMeta(imdbRating = "5"))
        assertEquals(3, cache.size)
        assertEquals("5", cache.get("b")?.imdbRating)
    }

    @Test
    fun `default cache stops at the documented limit`() {
        val cache = HeroMetaCache()
        repeat(HERO_META_CACHE_LIMIT + 8) { index ->
            cache.put("key$index", HomeHeroMeta())
        }
        assertEquals(HERO_META_CACHE_LIMIT, cache.size)
    }
}

private class FakeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String = "provider",
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var score: Score? = null,
) : SearchResponse
