/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/MetaPreview.kt (data shape only)
 */
package com.kodastream.app.ui.home.hero

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * CloudStream's [SearchResponse] shaped like Nuvio's `MetaPreview`, so the
 * ported hero files keep their field names unchanged.
 */
internal data class HeroItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val releaseInfo: String? = null,
    val status: String? = null,
    val ageRating: String? = null,
    val language: String? = null,
    val imdbRating: String? = null,
    /** Used only by the image requests, never shown. */
    val posterHeaders: Map<String, String>? = null,
    /** The item the hero clicked, so Home can open the same details page. */
    val source: SearchResponse? = null,
) {
    fun stableKey(): String = "$type:$id"
}

internal fun SearchResponse.toHeroItem(): HeroItem = HeroItem(
    id = url,
    type = heroItemType(type),
    name = name,
    poster = posterUrl,
    posterHeaders = posterHeaders,
    source = this,
)

/** "movie" for movies, "series" for series and anime, else the lower-case enum name. */
internal fun heroItemType(type: TvType?): String = when (type) {
    TvType.Movie, TvType.AnimeMovie -> "movie"
    TvType.TvSeries, TvType.Anime -> "series"
    null -> "movie"
    else -> type.name.lowercase()
}
