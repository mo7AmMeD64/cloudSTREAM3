package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The library's search-response constructors are deprecated in favour of suspend builders
// that need a provider instance; plain data objects are all these tests need.
@Suppress("DEPRECATION_ERROR")
class MoreWorksPresentationTest {
    private fun film(name: String, year: Int? = null, url: String = name, source: String = "source"): SearchResponse =
        MovieSearchResponse(name = name, url = url, apiName = source, type = TvType.Movie, year = year)

    private fun franchise(
        key: String,
        title: String? = null,
        year: Int? = null,
        part: Int = 0,
        season: Int? = null,
        cour: Int? = null,
        group: String? = null,
        order: Int = 0,
    ) = MoreWorksEntry(
        key = key, title = title, releaseYear = year, partOrder = part,
        seasonOrder = season, courOrder = cour, partGroupKey = group, relevanceOrder = order,
    )

    @Test
    fun franchiseReleasesUseChronologicalOrderAndUnknownDatesRemainLast() {
        val entries = listOf(
            franchise("unknown", "Unknown"),
            franchise("old", "Old", year = 2001),
            franchise("new", "New", year = 2025),
            franchise("middle", "Middle", year = 2015),
        )
        // One entry has no year, so the conservative fallback applies and keeps provider order;
        // with every year known the chronology is used instead.
        assertEquals(
            listOf("old", "middle", "new"),
            sortMoreWorksEntries(entries.filter { it.releaseYear != null }).map { it.key },
        )
        assertEquals("unknown", sortMoreWorksEntries(entries).last().key)
    }

    @Test
    fun releasesMarkedWithASeasonSortBeforeUnmarkedOnesAndPartsFollowTheirNumber() {
        // Built from real titles so the part/season/group fields come from the title parser.
        val entries = franchiseWorkEntries(
            listOf(
                film("Show Film", url = "film"),
                film("Show Part 2", url = "part-2"),
                film("Show Season 3", url = "season-3"),
                film("Show Part 1", url = "part-1"),
                film("Show Season 2", url = "season-2"),
            ),
        )
        assertEquals(
            listOf("season-2", "season-3", "part-1", "part-2", "film").map { "work:source|$it" },
            sortMoreWorksEntries(entries).map { it.key },
        )
    }

    @Test
    fun suggestionsAlwaysFollowTheFranchiseReleasesInProviderOrder() {
        val entries = listOf(
            MoreWorksEntry(key = "related-b", kind = MoreWorksEntryKind.RELATED, relevanceOrder = 1),
            franchise("part-2", part = 2),
            franchise("part-1", part = 1),
            MoreWorksEntry(key = "related-a", kind = MoreWorksEntryKind.RELATED, relevanceOrder = 0),
        )
        assertEquals(
            listOf("part-1", "part-2", "related-a", "related-b"),
            sortMoreWorksEntries(entries).map { it.key },
        )
    }

    @Test
    fun unmarkedNumericFilmSequelsUseNumericOrderWhenDatesAreMissing() {
        val entries = listOf(
            franchise("dune-10", "Dune 10", part = franchiseOrder("Dune 10"),
                group = franchisePartGroupKey("Dune 10")),
            franchise("dune-2", "Dune 2", part = franchiseOrder("Dune 2"),
                group = franchisePartGroupKey("Dune 2")),
        )
        assertEquals(listOf("dune-2", "dune-10"), sortMoreWorksEntries(entries).map { it.key })

        val badges = applyImmediateTimelineBadges(
            entries = entries,
            currentTitle = "Dune 2",
            currentReleaseYear = null,
        ).associateBy { it.key }
        assertEquals(MoreWorksBadge.NEXT, badges.getValue("dune-10").badge)
    }

    @Test
    fun partGroupsStayContiguousInsteadOfInterleavingSameNumberedParts() {
        val entries = listOf(
            franchise("b2", season = 3, cour = 1, part = 2, group = "other arc|season=3|cour=1"),
            franchise("a1", season = 3, cour = 1, part = 1, group = "main arc|season=3|cour=1"),
            franchise("b1", season = 3, cour = 1, part = 1, group = "other arc|season=3|cour=1"),
            franchise("a2", season = 3, cour = 1, part = 2, group = "main arc|season=3|cour=1"),
        )
        assertEquals(listOf("a1", "a2", "b1", "b2"), sortMoreWorksEntries(entries).map { it.key })
    }

    @Test
    fun partBadgesNeverJumpBetweenDifferentSeasonOrCourGroups() {
        val shared = "bleach thousand year blood war|season=3|cour=1"
        val result = applyImmediateTimelineBadges(
            entries = listOf(
                franchise("s3c1p2", season = 3, cour = 1, part = 2, group = shared),
                franchise("s4c1p1", season = 4, cour = 1, part = 1,
                    group = "bleach thousand year blood war|season=4|cour=1"),
            ),
            currentTitle = "Bleach Thousand-Year Blood War Season 3 Cour 1 Part 1",
            currentReleaseYear = null,
        ).associateBy { it.key }
        assertEquals(MoreWorksBadge.NEXT, result.getValue("s3c1p2").badge)
        assertNull(result.getValue("s4c1p1").badge)
    }

    @Test
    fun neighbourBadgesUseReleaseYearsWhenTheOpenTitleHasNoPartNumber() {
        val entries = listOf(
            franchise("first", year = 2001),
            franchise("older", year = 2005),
            franchise("newer", year = 2015),
            franchise("newest", year = 2020),
        )
        val badges = applyImmediateTimelineBadges(
            entries = entries,
            currentTitle = "Some Film",
            currentReleaseYear = 2010,
            currentYear = 2026,
        ).associateBy { it.key }
        assertNull(badges.getValue("first").badge)
        assertEquals(MoreWorksBadge.PREVIOUS, badges.getValue("older").badge)
        assertEquals(MoreWorksBadge.NEXT, badges.getValue("newer").badge)
        assertNull(badges.getValue("newest").badge)
    }

    @Test
    fun futureReleaseYearGetsComingSoonBadgeEvenWithoutLoadedTimestampMetadata() {
        val entries = listOf(
            franchise("released", year = 2020),
            franchise("future", year = 2030),
        )
        val badges = applyImmediateTimelineBadges(
            entries = entries,
            currentTitle = "Some Film",
            currentReleaseYear = 2020,
            currentYear = 2026,
        ).associateBy { it.key }
        assertEquals(MoreWorksBadge.COMING_SOON, badges.getValue("future").badge)
    }

    @Test
    fun providerVerifiedUpcomingWorkGetsComingSoonBadgeWithoutAYear() {
        val upcoming = franchise("upcoming", part = 2, group = "show").copy(verifiedUpcoming = true)
        val badges = applyImmediateTimelineBadges(
            entries = listOf(upcoming),
            currentTitle = "Show Part 1",
            currentReleaseYear = null,
            currentYear = 2026,
        ).associateBy { it.key }
        assertEquals(MoreWorksBadge.COMING_SOON, badges.getValue("upcoming").badge)
    }

    @Test
    fun titleSimilarityAloneNeverCreatesPreviousOrNext() {
        val badges = applyImmediateTimelineBadges(
            entries = listOf(franchise("sibling", "Show: Origins")),
            currentTitle = "Show",
            currentReleaseYear = null,
        ).associateBy { it.key }
        assertNull(badges.getValue("sibling").badge)
    }

    @Test
    fun relatedSuggestionsAreLabelledRelated() {
        val badges = applyImmediateTimelineBadges(
            entries = relatedWorkEntries(listOf(film("Match A"), film("Match B"))),
            currentTitle = "Open Title",
            currentReleaseYear = 2020,
        )
        assertTrue(badges.all { it.badge == MoreWorksBadge.RELATED })
    }

    @Test
    fun relatedEntriesKeepSourceIdentityAndUniqueWorks() {
        val entries = relatedWorkEntries(
            listOf(film("Best match", url = "best"), film("Duplicate label", url = "best"), film("Second match", url = "second")),
        )
        assertEquals(listOf("related:source|best", "related:source|second"), entries.map { it.key })
        assertEquals(listOf("Best match", "Second match"), entries.map { it.title })
    }

    @Test
    fun franchiseEntriesCarryTitleDerivedOrderingAndVerifiedUpcoming() {
        val entries = franchiseWorkEntries(
            items = listOf(film("Show Part 2", year = 2021, url = "p2"), film("Show Part 2", url = "p2")),
            upcomingKeys = setOf("source|p2"),
        )
        assertEquals(1, entries.size)
        assertEquals(2, entries.single().partOrder)
        assertEquals(2021, entries.single().releaseYear)
        assertTrue(entries.single().verifiedUpcoming)
        assertEquals("work:source|p2", entries.single().key)
    }

    @Test
    fun stableIdentityRemovesDuplicateEntries() {
        val entries = listOf(franchise("movie:2", "Film", year = 2020), franchise("movie:2", "Film", year = 2020))
        assertEquals(listOf("movie:2"), sortMoreWorksEntries(entries).map { it.key })
    }

    @Test
    fun duplicateSearchSeasonIsSuppressedButExplicitPartAndCourEntriesRemain() {
        val entries = listOf(
            franchise("duplicate-season-2", "Show Season 2", season = 2),
            franchise("season-2-cour-part", "Show Season 2 Cour 1 Part 2", season = 2, cour = 1, part = 2),
            franchise("part-only", "Show Part 3", part = 3),
            franchise("season-9", "Show Season 9", season = 9),
        )
        assertEquals(
            listOf("season-2-cour-part", "part-only", "season-9"),
            excludeFranchiseEntriesAlreadyCoveredBySeasons(entries, setOf(1, 2, 3)).map { it.key },
        )
    }

    @Test
    fun railBoundsOptionalReleasesAndSuggestions() {
        val entries = buildList {
            repeat(30) { part -> add(franchise("part-$part", "Part ${part + 1}", part = part + 1)) }
            repeat(12) { match ->
                add(MoreWorksEntry(key = "related-$match", kind = MoreWorksEntryKind.RELATED, relevanceOrder = match))
            }
        }
        val bounded = boundMoreWorksEntries(entries)
        assertEquals(FRANCHISE_EXTRA_LIMIT, bounded.count { it.kind == MoreWorksEntryKind.FRANCHISE })
        assertEquals(RELATED_ROW_LIMIT, bounded.count { it.kind == MoreWorksEntryKind.RELATED })
        assertEquals(FRANCHISE_EXTRA_LIMIT + RELATED_ROW_LIMIT, bounded.size)
    }

    @Test
    fun sameYearNamedFranchiseArcsKeepProviderOrderInsteadOfAlphabeticalOrder() {
        val entries = listOf(
            franchise("zeta", "Zeta Arc", year = 2020, order = 0),
            franchise("alpha", "Alpha Arc", year = 2020, order = 1),
        )
        assertEquals(listOf("zeta", "alpha"), sortMoreWorksEntries(entries).map { it.key })
    }

    @Test
    fun builtListIsBoundedOrderedAndLabelledEndToEnd() {
        val built = buildMoreWorksEntries(
            franchise = listOf(
                film("Show Part 2", url = "p2"),
                film("Show Season 2", url = "dup-season"),
                film("Show Part 1", url = "p1"),
            ),
            similarByTags = listOf(film("Another Title", url = "other")),
            upcomingKeys = setOf("source|p2"),
            realSeasonNumbers = setOf(1, 2),
            currentTitle = "Show",
            currentReleaseYear = null,
            currentYear = 2026,
        )
        // "Season 2" is covered by the page's own season chips and is removed.
        assertEquals(
            listOf("work:source|p1", "work:source|p2", "related:source|other"),
            built.map { it.key },
        )
        assertEquals(
            listOf(null, MoreWorksBadge.COMING_SOON, MoreWorksBadge.RELATED),
            built.map { it.badge },
        )
    }
}
