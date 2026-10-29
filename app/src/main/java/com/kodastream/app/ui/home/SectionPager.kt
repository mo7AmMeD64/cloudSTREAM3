package com.kodastream.app.ui.home

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.SearchResponse

data class SectionMerge(val items: List<SearchResponse>, val hasNext: Boolean)

/**
 * Adds one fetched page to a section. A null [lists] means the request failed.
 * A page that brings nothing new ends paging even when the provider still
 * reports a next page, so a broken provider cannot loop forever.
 */
fun mergeSectionPage(
    current: List<SearchResponse>,
    lists: List<HomePageList>?,
    responseHasNext: Boolean,
    sectionName: String,
): SectionMerge {
    if (lists == null) return SectionMerge(current, false)
    // Match by list name; a single-list response is the same section even when
    // the provider renamed it.
    val page = lists.firstOrNull { it.name == sectionName }
        ?: lists.singleOrNull()
        ?: return SectionMerge(current, false)
    val seen = current.mapTo(HashSet()) { it.url }
    val added = page.list.filter { seen.add(it.url) }
    return SectionMerge(current + added, responseHasNext && added.isNotEmpty())
}
