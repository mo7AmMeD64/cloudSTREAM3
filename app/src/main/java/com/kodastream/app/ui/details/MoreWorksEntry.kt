package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.SearchResponse

/** One card of the "More" rail: another release of the same franchise, or a related work. */
internal data class MoreWorksEntry(
    val key: String,
    val title: String? = null,
    val releaseYear: Int? = null,
    /**
     * Ordinal of a "part / release" parsed from the title (Part 2 -> 2). Providers that
     * split one work into several releases instead of real seasons use this so the parts
     * can be ordered like continuations. 0 means "no part marker found".
     */
    val partOrder: Int = 0,
    /** Explicit season marker parsed from a split release title (not a provider season key). */
    val seasonOrder: Int? = null,
    /** Explicit cour marker; kept separate from season and part numbering. */
    val courOrder: Int? = null,
    /** Normalized title family that owns this part/cour, including its explicit season scope. */
    val partGroupKey: String? = null,
    val work: SearchResponse? = null,
    /** Franchise releases come first in release order; genre matches always follow them. */
    val kind: MoreWorksEntryKind = MoreWorksEntryKind.FRANCHISE,
    /** Stable provider-relevance order; used as the last tie-breaker. */
    val relevanceOrder: Int = 0,
    /** True only when the owning provider's loaded metadata confirms a future release. */
    val verifiedUpcoming: Boolean = false,
    /** Immediate relation to the open work; never inferred from list position alone. */
    val badge: MoreWorksBadge? = null,
)

internal enum class MoreWorksEntryKind { FRANCHISE, RELATED }

internal enum class MoreWorksBadge { PREVIOUS, NEXT, COMING_SOON, RELATED }
