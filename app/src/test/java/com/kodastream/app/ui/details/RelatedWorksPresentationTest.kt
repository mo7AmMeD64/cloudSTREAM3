package com.kodastream.app.ui.details

import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedWorksPresentationTest {
    @Test
    fun standaloneWorkWithoutFranchiseEvidenceDoesNotTriggerFallbackSearch() {
        assertFalse(shouldSearchRelatedCandidates(franchiseRecommendationCount = 0, hasExplicitPartMarker = false))
    }

    @Test
    fun explicitlyNumberedPartCanDiscoverItsSiblingReleases() {
        assertTrue(shouldSearchRelatedCandidates(franchiseRecommendationCount = 0, hasExplicitPartMarker = true))
    }

    @Test
    fun existingFranchiseRecommendationCanJustifyBoundedDiscovery() {
        assertTrue(shouldSearchRelatedCandidates(franchiseRecommendationCount = 2, hasExplicitPartMarker = false))
    }

    @Test
    fun rootMoviesAndAnimeCanTryOneStrictlyFilteredDiscoverySearch() {
        assertTrue(shouldSearchRelatedCandidates(
            franchiseRecommendationCount = 0,
            hasExplicitPartMarker = false,
            allowRootTitleSearch = true,
        ))
        assertFalse(shouldSearchRelatedCandidates(
            franchiseRecommendationCount = 0,
            hasExplicitPartMarker = false,
        ))
        assertFalse(shouldSearchRelatedCandidates(
            franchiseRecommendationCount = FRANCHISE_EXTRA_LIMIT,
            hasExplicitPartMarker = false,
            allowRootTitleSearch = true,
        ))
    }

    @Test
    fun subtitleFranchisePrefixCanJustifyBoundedDiscoveryWithoutNumberedPart() {
        assertEquals("Pirates of the Caribbean", franchisePrefixQuery("Pirates of the Caribbean: Dead Man's Chest"))
        assertTrue(shouldSearchRelatedCandidates(
            franchiseRecommendationCount = 0,
            hasExplicitPartMarker = false,
            hasSubtitleFranchisePrefix = true,
        ))
        assertEquals(null, franchisePrefixQuery("Up: A Short Adventure"))
        assertEquals(null, franchisePrefixQuery("Standalone title"))
    }

    @Test
    fun fullFranchiseLaneDoesNotTriggerMoreSearches() {
        assertFalse(shouldSearchRelatedCandidates(franchiseRecommendationCount = FRANCHISE_EXTRA_LIMIT, hasExplicitPartMarker = true))
        assertFalse(shouldSearchRelatedCandidates(franchiseRecommendationCount = FRANCHISE_EXTRA_LIMIT, hasExplicitPartMarker = false))
    }

    @Test
    fun metadataLoadsRequireUsefulGenreTagsOnTheCurrentWork() {
        assertFalse(hasComparableGenreTags(emptyList()))
        assertFalse(hasComparableGenreTags(listOf("Anime", "Movie", "Series")))
        assertTrue(hasComparableGenreTags(listOf("Adventure", "Fantasy")))
    }

    private data class Item(
        override val name: String,
        override val url: String = name,
        override var apiName: String = "same-source",
        override var type: TvType? = null,
        override var posterUrl: String? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var id: Int? = null,
        override var quality: com.lagradost.cloudstream3.SearchQuality? = null,
        override var score: com.lagradost.cloudstream3.Score? = null,
    ) : SearchResponse


    @Test
    fun parsesGluedSeasonAbbreviationsAndArabicIndicPartNumbers() {
        val seasonTwo = parseTitleParts("My Hero Academia S02")
        assertEquals(2, seasonTwo.markerNumber)
        assertEquals("s", seasonTwo.markerKind)
        assertEquals(listOf("my", "hero", "academia"), seasonTwo.base)
        assertEquals(2, seasonTwo.seasonNumber)
        assertEquals(null, seasonTwo.partNumber)
        assertEquals(0, franchiseOrder("My Hero Academia S02"))

        val compound = parseTitleParts("Bleach Thousand-Year Blood War Season 3 Cour 2 Part 1")
        assertEquals(3, compound.seasonNumber)
        assertEquals(2, compound.courNumber)
        assertEquals(1, compound.partNumber)
        assertEquals(
            "bleach thousand year blood war|season=3|cour=2",
            compound.partGroupKey,
        )
        assertEquals(1, franchiseOrder("Bleach Thousand-Year Blood War Season 3 Part 1"))
        assertEquals(0, franchiseOrder("Catch-22"))
        assertEquals(0, franchiseOrder("World War 2"))
        assertEquals(0, franchiseOrder("Film 2"))
        assertEquals(2, franchiseOrder("Dune 2"))
        assertEquals(10, franchiseOrder("Dune 10"))
        assertTrue(looksLikeSameFranchise("A Series Season 2", "A Series Season 3"))

        val arabicPart = parseTitleParts("مسلسل الجزء ٢")
        assertEquals(2, arabicPart.markerNumber)
        assertEquals("جزء", arabicPart.markerKind)
        assertEquals(listOf("مسلسل"), arabicPart.base)
        assertTrue(looksLikeSameFranchise("ون بيس الجزء ١", "ون بيس الجزء ٢"))

        // Part labels also occur as Roman numerals and cardinal words (not only 2/II).
        assertEquals(2, parseTitleParts("JoJo's Bizarre Adventure Part II").partNumber)
        assertEquals(2, parseTitleParts("JoJo's Bizarre Adventure Part Two").partNumber)
        val romanCompound = parseTitleParts("Bleach Season Three Cour II Part I")
        assertEquals(3, romanCompound.seasonNumber)
        assertEquals(2, romanCompound.courNumber)
        assertEquals(1, romanCompound.partNumber)
    }

    @Test
    fun recognizesFranchiseTitlesWithoutInventingRelationsFromNumbersAlone() {
        assertTrue(looksLikeSameFranchise("Naruto", "Naruto Shippuden"))
        assertFalse(looksLikeSameFranchise("Pirates of the Caribbean", "Pirates of Silicon Valley"))
        assertFalse(looksLikeSameFranchise("Pirates of the Caribbean: Dead Man's Chest", "Pirates of Silicon Valley"))
        // One shared word that does not lead both titles is not enough evidence; this
        // strict rule is what keeps unrelated works out of the "More" row.
        assertFalse(looksLikeSameFranchise("Naruto", "Boruto: Naruto Next Generations"))
        assertTrue(looksLikeSameFranchise("ون بيس", "ون بيس فيلم ريد"))
        assertFalse(looksLikeSameFranchise("ون بيس", "بيس من العقل"))
        assertTrue(looksLikeSameFranchise("The Hero", "The Hero Part Two"))
        assertTrue(looksLikeSameFranchise("My Hero Academia", "My Hero Academia: Two Heroes"))
        assertTrue(looksLikeSameFranchise(
            "Pirates of the Caribbean: Dead Man's Chest",
            "Pirates of the Caribbean: At World's End",
        ))
        assertFalse(looksLikeSameFranchise("Blue Lock", "Red Lock"))
        // Bare sequel numbers are not enough to collapse distinct franchises.
        assertFalse(looksLikeSameFranchise("World War 2", "World War Z"))
        assertFalse(looksLikeSameFranchise("World War Z", "World War 2"))
        assertFalse(looksLikeSameFranchise("World War 2", "World War 3"))
        assertFalse(looksLikeSameFranchise("Film 2", "Film 3"))
        assertTrue(looksLikeSameFranchise("Dune", "Dune 2"))
        assertTrue(looksLikeSameFranchise("Dune 2", "Dune 3"))
        assertTrue(looksLikeSameFranchise(
            "JoJo's Bizarre Adventure",
            "JoJo's Bizarre Adventure: Stardust Crusaders",
        ))
        assertTrue(looksLikeSameFranchise(
            "JoJo's Bizarre Adventure: Stardust Crusaders",
            "JoJo's Bizarre Adventure: Diamond Is Unbreakable",
        ))
        assertTrue(looksLikeSameFranchise("Chainsaw Man", "Chainsaw Man: Reze Arc"))
        assertTrue(looksLikeSameFranchise("One Piece", "The One Piece"))
        assertTrue(looksLikeSameFranchise("One Piece", "One Piece Remake"))
    }

    @Test
    fun sameFranchiseCandidatesDoNotConsumeTheGenreMetadataBudget() {
        val candidates = listOf(
            Item("Current Work Part 2"),
            Item("Current Work Part 3"),
            Item("Unrelated Adventure"),
            Item("Unrelated Mystery"),
        )
        val selected = candidatesRequiringRelatedMetadata(
            currentTitle = "Current Work Part 1",
            candidates = candidates,
            limit = 2,
        )
        assertEquals(listOf("Unrelated Adventure", "Unrelated Mystery"), selected.map { it.name })
    }


    @Test
    fun metadataBudgetIsSharedBetweenRecommendationsAndFallbackSearch() {
        val recommendations = (1..12).map { Item("Unrelated recommendation $it", url = "rec$it") }
        val searchResults = (1..12).map { Item("Search candidate $it", url = "search$it") }
        val selected = candidatesForRelatedMetadata(
            currentTitle = "Current Work",
            recommendations = recommendations,
            searchResults = searchResults,
        )
        assertEquals(RELATED_ENRICH_LIMIT, selected.size)
        assertEquals(2, selected.count { it.url.startsWith("rec") })
        assertEquals(2, selected.count { it.url.startsWith("search") })
    }

    @Test
    fun metadataBudgetUsesTheOtherLaneWhenOneLaneIsShort() {
        val recommendations = listOf(Item("Recommendation", url = "rec"))
        val searchResults = (1..8).map { Item("Search candidate $it", url = "search$it") }
        val selected = candidatesForRelatedMetadata(
            currentTitle = "Current Work",
            recommendations = recommendations,
            searchResults = searchResults,
        )
        assertEquals(RELATED_ENRICH_LIMIT, selected.size)
        assertEquals(1, selected.count { it.url == "rec" })
        assertEquals(3, selected.count { it.url.startsWith("search") })
    }

    @Test
    fun metadataBudgetDoesNotSpendSlotsOnFranchiseEntriesOrDuplicates() {
        val recommendations = listOf(
            Item("Current Work Part 2", url = "part2"),
            Item("Same result", url = "dup"),
        )
        val searchResults = listOf(
            Item("Same result duplicate", url = "dup"),
            Item("Other candidate", url = "other"),
        )
        val selected = candidatesForRelatedMetadata(
            currentTitle = "Current Work Part 1",
            recommendations = recommendations,
            searchResults = searchResults,
        )
        assertEquals(listOf("Same result", "Other candidate"), selected.map { it.name })
    }

    @Test
    fun normalizesArabicAndEnglishGenreAliases() {
        assertEquals(setOf("comedy"), normalizeGenreTags("كوميدي"))
        assertEquals(setOf("shounen"), normalizeGenreTags("Shōnen"))
        assertEquals(setOf("superpower"), normalizeGenreTags("قوى خارقة"))
        assertEquals(setOf("supernatural"), normalizeGenreTags("خارق للطبيعة"))
        assertEquals(setOf("food"), normalizeGenreTags("Cooking"))
        assertEquals(setOf("science-fiction"), normalizeGenreTags("خيال علمي"))
        assertEquals(setOf("science-fiction", "fantasy"), normalizeGenreTags("Science Fiction / Fantasy"))
        assertEquals(setOf("science-fiction", "fantasy"), normalizeGenreTags("خيال علمي وفانتازيا"))
        assertEquals(setOf("science-fiction", "fantasy"), normalizeGenreTags("الخيال العلمي والفانتازيا"))
        assertEquals(setOf("action", "science-fiction", "superpower"), normalizeGenreTags("قتال وخيال علمي وقوى خارقة"))
        assertEquals(setOf("reverse-harem"), normalizeGenreTags("Reverse Harem"))
        assertEquals(setOf("psychological"), normalizeGenreTags("Mind Games"))
    }

    @Test
    fun urlIdentityPreservesContentQueryParametersAndDropsOnlyTrackingData() {
        val first = normalizeUrlKey("https://EXAMPLE.test/title/?id=101&utm_source=campaign#details")
        val same = normalizeUrlKey("https://example.test/title?id=101&fbclid=tracking")
        val other = normalizeUrlKey("https://example.test/title?id=102")
        assertEquals(first, same)
        assertFalse(first == other)
    }

    @Test
    fun sameRelativeUrlFromDifferentProvidersIsNotCollapsedByMetaprovider() {
        val first = Item("Series from A", url = "/title/", apiName = "provider-a")
        val second = Item("Series from B", url = "/title/", apiName = "provider-b")
        val rows = buildRelatedWorkRows(
            currentApiName = "meta",
            currentTitle = "Current Work",
            currentTags = listOf("Adventure"),
            candidates = listOf(
                RelatedCandidate(first, listOf("Adventure")),
                RelatedCandidate(second, listOf("Adventure")),
            ),
            currentUrl = "/current",
            includeForeignSources = true,
        )
        assertEquals(2, rows.similarByTags.size)
        assertEquals(setOf("provider-a", "provider-b"), rows.similarByTags.map { it.apiName }.toSet())
    }

    @Test
    fun supportsPsychologicalMysteryCrimeAndAdventureGenres() {
        assertEquals(setOf("psychological", "mystery"), normalizeGenreTags("Psychological / Mystery"))
        assertEquals(setOf("detective"), normalizeGenreTags("تحقيق"))
        assertEquals(setOf("crime"), normalizeGenreTags("جريمة"))
        assertEquals(setOf("adventure"), normalizeGenreTags("مغامرة"))
        assertEquals(setOf("thriller"), normalizeGenreTags("إثارة"))
        assertEquals(setOf("mecha"), normalizeGenreTags("ميكا"))
        assertEquals(setOf("shoujo"), normalizeGenreTags("Shoujo"))
        assertEquals(setOf("superhero"), normalizeGenreTags("بطل خارق"))
        assertEquals(setOf("survival"), normalizeGenreTags("Survival"))
    }

    @Test
    fun ignoresFormatOnlyTagsInsteadOfMatchingEveryAnime() {
        assertTrue(normalizeGenreTags("Anime").isEmpty())
        assertTrue(normalizeGenreTags("فيلم").isEmpty())
    }

    @Test
    fun similarWorksAreRankedByNumberOfMatchingGenres() {
        val oneMatch = Item("One Match")
        val twoMatches = Item("Two Matches")
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "Current Work",
            currentTags = listOf("Adventure", "Mystery", "Comedy"),
            candidates = listOf(
                RelatedCandidate(oneMatch, listOf("Adventure")),
                RelatedCandidate(twoMatches, listOf("Mystery", "Comedy")),
            ),
        )
        assertEquals(listOf("Two Matches", "One Match"), rows.similarByTags.map { it.name })
    }

    @Test
    fun doesNotCallUntaggedSearchNeighboursRelatedWithoutVerifiedGenreOverlap() {
        val verified = Item("Verified match")
        val unverified = Item("Unverified neighbour")
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "Current Work",
            currentTags = listOf("Adventure", "Mystery"),
            candidates = listOf(
                RelatedCandidate(verified, listOf("Adventure")),
                RelatedCandidate(unverified, emptyList()),
            ),
        )
        assertEquals(listOf("Verified match"), rows.similarByTags.map { it.name })
    }

    @Test
    fun separatesFranchiseSequelsFromGenreSimilarityResults() {
        val sequel = Item("The Hero Part Two")
        val genreMatch = Item("Another Adventure")
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "The Hero",
            currentTags = listOf("Adventure", "Fantasy"),
            candidates = listOf(
                RelatedCandidate(sequel, listOf("Adventure", "Fantasy")),
                RelatedCandidate(genreMatch, listOf("Adventure", "Fantasy")),
            ),
        )
        assertEquals(listOf("The Hero Part Two"), rows.franchise.map { it.name })
        assertEquals(listOf("Another Adventure"), rows.similarByTags.map { it.name })
    }

    @Test
    fun capsOptionalGenreSuggestionsForTheExpandedPage() {
        val candidates = (1..14).map { index ->
            RelatedCandidate(Item("Related $index"), listOf("Adventure", "Fantasy"))
        }
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "Current Work",
            currentTags = listOf("Adventure", "Fantasy"),
            candidates = candidates,
        )
        assertEquals(RELATED_ROW_LIMIT, rows.similarByTags.size)
    }

    @Test
    fun capsOptionalSameFranchiseExtrasWithoutTouchingSeasonEntries() {
        val candidates = (1..36).map { index ->
            RelatedCandidate(Item("The Hero Part $index"), emptyList())
        }
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "The Hero",
            currentTags = emptyList(),
            candidates = candidates,
        )
        assertEquals(FRANCHISE_EXTRA_LIMIT, rows.franchise.size)
    }

    @Test
    fun doesNotTreatOneGenericSharedTitleWordAsAFranchise() {
        assertFalse(looksLikeSameFranchise("One Piece", "Piece of Mind"))
        assertTrue(looksLikeSameFranchise("One Piece", "One Piece Film Red"))
    }

    @Test
    fun removesDuplicatesAndNeverMixesSourceIdentities() {
        val item = Item("Related", url = "same")
        val duplicate = Item("Duplicate", url = "same")
        val otherSource = Item("Wrong source", url = "wrong", apiName = "other-source")
        val rows = buildRelatedWorkRows(
            currentApiName = "same-source",
            currentTitle = "Current",
            currentTags = listOf("Comedy"),
            candidates = listOf(
                RelatedCandidate(item, listOf("Comedy")),
                RelatedCandidate(duplicate, listOf("Comedy")),
                RelatedCandidate(otherSource, listOf("Comedy")),
            ),
        )
        assertEquals(1, rows.similarByTags.size)
        assertEquals("same-source", rows.similarByTags.single().apiName)
    }
    @Test
    fun urlIdentityRetainsRouteFragmentsWhenTheyMayContainTheWorkId() {
        val first = normalizeUrlKey("https://example.test/#/movie/101")
        val other = normalizeUrlKey("https://example.test/#/movie/102")
        val sameWithoutPresentationFragment = normalizeUrlKey("https://example.test/#details")
        assertFalse(first == other)
        assertEquals(normalizeUrlKey("https://example.test/"), sameWithoutPresentationFragment)
    }

    @Test
    fun arabicOrdinalVariantsAreParsedAsNumberedParts() {
        assertEquals(6, franchiseOrder("العمل الجزء السادسة"))
        assertEquals(7, franchiseOrder("العمل الجزء السابعة"))
        assertEquals(8, franchiseOrder("العمل الجزء الثامنة"))
        assertEquals(9, franchiseOrder("العمل الجزء التاسعة"))
        assertEquals(10, franchiseOrder("العمل الجزء العاشرة"))
    }

}
