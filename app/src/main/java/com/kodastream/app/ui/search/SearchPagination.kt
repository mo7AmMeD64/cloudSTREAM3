package com.kodastream.app.ui.search

import com.lagradost.cloudstream3.SearchResponse

/**
 * Returns true if any provider reported hasNext = true on its last page.
 */
fun shouldShowLoadMore(lastPages: Map<String, Boolean>): Boolean =
    lastPages.values.any { it }

/**
 * Appends [incoming] items to [current], discarding any item whose URL is
 * already present in [current] or earlier in [incoming].
 */
fun appendSearchItems(
    current: List<SearchResponse>,
    incoming: List<SearchResponse>,
): List<SearchResponse> {
    val seen = current.mapTo(HashSet()) { it.url }
    return current + incoming.filter { seen.add(it.url) }
}

/**
 * Merges [incoming] search items into the matching [SearchGroup] in [groups],
 * de-duplicated by item URL. If no group exists for [apiName], a new group is created.
 */
fun mergeSearchGroup(
    groups: List<SearchGroup>,
    apiName: String,
    incoming: List<SearchResponse>,
): List<SearchGroup> {
    val index = groups.indexOfFirst { it.apiName == apiName }
    if (index >= 0) {
        val existing = groups[index]
        val mergedItems = appendSearchItems(existing.items, incoming)
        return groups.toMutableList().apply {
            set(index, existing.copy(items = mergedItems))
        }
    }
    return groups + SearchGroup(apiName, incoming)
}
