package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import java.util.Locale

/**
 * One deterministic order for the "More" rail. If every franchise release has a
 * provider-supplied release year, that chronology is used so a continuation film can
 * sit between other releases. If dates are incomplete, fall back to the conservative
 * season/part order rather than guessing where an undated work belongs. Genre
 * suggestions always remain after the franchise releases.
 */
internal fun sortMoreWorksEntries(entries: List<MoreWorksEntry>): List<MoreWorksEntry> {
    val unique = entries.distinctBy { it.key }
    val timeline = unique.filter { it.kind == MoreWorksEntryKind.FRANCHISE }
    val related = unique.filter { it.kind == MoreWorksEntryKind.RELATED }
        .sortedWith(compareBy<MoreWorksEntry> { it.relevanceOrder }.thenBy { it.key })

    // A release explicitly marked "Season 3" sorts by that season; releases with no
    // season marker follow them.
    fun timelinePosition(entry: MoreWorksEntry): Long =
        entry.seasonOrder?.takeIf { it > 0 }?.let { it.toLong() * 100L + 25L } ?: (Long.MAX_VALUE / 2L)

    val comparator = compareBy<MoreWorksEntry> { timelinePosition(it) }
        .thenBy { it.courOrder ?: Int.MAX_VALUE }
        // Group first, then order parts within that group. Sorting by part number
        // before group identity interleaved Part 1 from unrelated arcs with Part 2
        // from the first arc (especially when providers expose multiple split runs).
        .thenBy { it.partGroupKey.orEmpty() }
        .thenBy { it.partOrder.takeIf { order -> order > 0 } ?: Int.MAX_VALUE }
        .thenBy { it.releaseYear ?: Int.MAX_VALUE }
        // Retain the provider's order for same-year/same-part titles. A lexical
        // fallback can invert named arcs that have no explicit Part marker.
        .thenBy { it.relevanceOrder }
        .thenBy { it.title.orEmpty().lowercase(Locale.ROOT) }
        .thenBy { it.key }

    // When every release has a year, release chronology is stronger than title-derived
    // numbering. With partial dates, retain explicit season/part grouping and do not
    // guess where an undated work belongs.
    val sortedTimeline = if (timeline.size > 1 && timeline.all { it.releaseYear != null }) {
        timeline.sortedWith(compareBy<MoreWorksEntry> { it.releaseYear }.then(comparator))
    } else {
        timeline.sortedWith(comparator)
    }
    return sortedTimeline + related
}

/**
 * Remove a search result that is merely another provider page for a season the open
 * title already lists as real episodes. The season chips on the page cover it. Keep
 * explicit cour/part pages: those may be separately published story segments.
 */
internal fun excludeFranchiseEntriesAlreadyCoveredBySeasons(
    entries: List<MoreWorksEntry>,
    realSeasonNumbers: Set<Int>,
): List<MoreWorksEntry> = entries.filterNot { entry ->
    entry.kind == MoreWorksEntryKind.FRANCHISE &&
        entry.seasonOrder != null && entry.seasonOrder in realSeasonNumbers &&
        entry.partOrder <= 0 && entry.courOrder == null
}

/** Defensive bound for the rail: optional franchise releases and suggestions are capped. */
internal fun boundMoreWorksEntries(entries: List<MoreWorksEntry>): List<MoreWorksEntry> {
    val ordered = sortMoreWorksEntries(entries)
    val franchise = ordered.filter { it.kind == MoreWorksEntryKind.FRANCHISE }
        .take(FRANCHISE_EXTRA_LIMIT)
    val related = ordered.filter { it.kind == MoreWorksEntryKind.RELATED }
        .take(RELATED_ROW_LIMIT)
    return sortMoreWorksEntries(franchise + related)
}

/**
 * Add relation labels only to the immediate neighbours of the open work. An explicit
 * part number in the open title wins; otherwise a strict release-year comparison is
 * used; with neither, only an explicitly numbered later part can be labelled "next".
 * Title similarity alone never creates a previous/next label.
 */
internal fun applyImmediateTimelineBadges(
    entries: List<MoreWorksEntry>,
    currentTitle: String,
    currentReleaseYear: Int?,
    currentYear: Int = java.time.Year.now().value,
): List<MoreWorksEntry> {
    val ordered = sortMoreWorksEntries(entries)
    val badges = mutableMapOf<String, MoreWorksBadge>()
    val franchise = ordered.filter { it.kind == MoreWorksEntryKind.FRANCHISE }

    if (franchise.isNotEmpty()) {
        val currentPart = franchiseOrder(currentTitle).takeIf { it > 0 }
        val currentPartGroup = parseTitleParts(currentTitle).partGroupKey
        val previous: MoreWorksEntry?
        val next: MoreWorksEntry?
        if (currentPart != null) {
            val samePartGroup = franchise.filter { item ->
                item.partOrder > 0 && currentPartGroup != null && item.partGroupKey == currentPartGroup
            }
            previous = samePartGroup.filter { it.partOrder in 1 until currentPart }
                .maxByOrNull { it.partOrder }
            next = samePartGroup.filter { it.partOrder > currentPart }
                .minByOrNull { it.partOrder }
        } else if (currentReleaseYear != null) {
            // A supplied release year outranks a guessed part-number relationship.
            previous = franchise.filter { item ->
                item.releaseYear?.let { it < currentReleaseYear } == true
            }.maxByOrNull { it.releaseYear ?: Int.MIN_VALUE }
            next = franchise.filter { item ->
                item.releaseYear?.let { it > currentReleaseYear } == true
            }.minByOrNull { it.releaseYear ?: Int.MAX_VALUE }
        } else {
            // With neither an explicit part number nor a release year, only an
            // explicitly numbered later part can be considered the next item.
            previous = null
            next = franchise
                .filter { it.partOrder > 1 && currentPartGroup != null && it.partGroupKey == currentPartGroup }
                .minByOrNull { it.partOrder }
        }
        previous?.let { badges[it.key] = MoreWorksBadge.PREVIOUS }
        next?.let {
            badges[it.key] = if (isComingSoonEntry(it, currentYear)) MoreWorksBadge.COMING_SOON else MoreWorksBadge.NEXT
        }
    }

    return ordered.map { entry ->
        val badge = badges[entry.key] ?: if (entry.kind == MoreWorksEntryKind.RELATED) {
            MoreWorksBadge.RELATED
        } else null
        entry.copy(badge = badge)
    }
}

/** A future provider-supplied year is enough for a provisional Coming Soon badge. */
internal fun isComingSoonEntry(entry: MoreWorksEntry, currentYear: Int): Boolean =
    entry.verifiedUpcoming || (entry.releaseYear != null && entry.releaseYear > currentYear)

@Suppress("DEPRECATION_ERROR")
internal fun releaseYearOf(item: SearchResponse): Int? = when (item) {
    is AnimeSearchResponse -> item.year
    is MovieSearchResponse -> item.year
    is TvSeriesSearchResponse -> item.year
    else -> null
}

internal fun franchiseWorkEntries(
    items: List<SearchResponse>,
    upcomingKeys: Set<String> = emptySet(),
): List<MoreWorksEntry> =
    items.distinctBy { it.apiName to normalizeUrlKey(it.url) }.mapIndexed { index, item ->
        val identity = "${item.apiName}|${normalizeUrlKey(item.url)}"
        MoreWorksEntry(
            key = "work:$identity",
            title = item.name,
            releaseYear = releaseYearOf(item),
            // Part ordering so provider-split releases read as continuations.
            partOrder = franchiseOrder(item.name),
            seasonOrder = franchiseSeasonOrder(item.name),
            courOrder = franchiseCourOrder(item.name),
            partGroupKey = franchisePartGroupKey(item.name),
            work = item,
            relevanceOrder = index,
            verifiedUpcoming = identity in upcomingKeys,
        )
    }

internal fun relatedWorkEntries(items: List<SearchResponse>): List<MoreWorksEntry> =
    items.distinctBy { it.apiName to normalizeUrlKey(it.url) }.mapIndexed { index, item ->
        MoreWorksEntry(
            key = "related:${item.apiName}|${normalizeUrlKey(item.url)}",
            title = item.name,
            releaseYear = releaseYearOf(item),
            work = item,
            kind = MoreWorksEntryKind.RELATED,
            relevanceOrder = index,
        )
    }

/**
 * The finished, bounded and labelled list the rail renders: franchise releases (minus
 * pages the season chips already cover) in release order, then verified genre matches.
 */
internal fun buildMoreWorksEntries(
    franchise: List<SearchResponse>,
    similarByTags: List<SearchResponse>,
    upcomingKeys: Set<String>,
    realSeasonNumbers: Set<Int>,
    currentTitle: String,
    currentReleaseYear: Int?,
    currentYear: Int = java.time.Year.now().value,
): List<MoreWorksEntry> {
    val franchiseEntries = excludeFranchiseEntriesAlreadyCoveredBySeasons(
        entries = franchiseWorkEntries(franchise, upcomingKeys),
        realSeasonNumbers = realSeasonNumbers,
    )
    val bounded = boundMoreWorksEntries(franchiseEntries + relatedWorkEntries(similarByTags))
    return applyImmediateTimelineBadges(
        entries = bounded,
        currentTitle = currentTitle,
        currentReleaseYear = currentReleaseYear,
        currentYear = currentYear,
    )
}
