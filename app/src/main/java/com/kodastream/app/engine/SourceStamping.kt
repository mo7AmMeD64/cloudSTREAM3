package com.kodastream.app.engine

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse

/** The source name a result is stamped with: its own when it has one, else its provider's. */
internal fun sourceNameFor(declared: String?, providerName: String): String =
    if (declared.isNullOrBlank()) providerName else declared

/** Results without a url can never be opened, so they are dropped at the boundary. */
internal fun keepResult(url: String?): Boolean = !url.isNullOrBlank()

/**
 * Returns [item] carrying [apiName] as its source. `SearchResponse.apiName` is read-only,
 * so a result that names another source is replaced by a copy of the same concrete type
 * (the copy keeps every other field, so type-specific badges keep working). An item that
 * already carries [apiName], or a result type this app does not know, is returned as is.
 */
@Suppress("UNCHECKED_CAST", "DEPRECATION_ERROR")
internal fun <T : SearchResponse> withSource(item: T, apiName: String): T = when {
    item.apiName == apiName -> item
    item is AnimeSearchResponse -> item.copy(apiName = apiName)
    item is MovieSearchResponse -> item.copy(apiName = apiName)
    item is TvSeriesSearchResponse -> item.copy(apiName = apiName)
    item is LiveSearchResponse -> item.copy(apiName = apiName)
    item is TorrentSearchResponse -> item.copy(apiName = apiName)
    else -> item
} as T

/** Drops results that cannot be opened and stamps the rest with their provider's name. */
internal fun <T : SearchResponse> stampResults(providerName: String, items: List<T>): List<T> =
    items
        .filter { item -> keepResult(item.url) }
        .map { item -> withSource(item, sourceNameFor(item.apiName, providerName)) }
