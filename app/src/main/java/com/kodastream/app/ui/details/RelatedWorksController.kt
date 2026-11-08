package com.kodastream.app.ui.details

import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the "More" rail shows: same-franchise releases first, then verified genre matches. */
data class DetailsRelatedState(
    val loading: Boolean = false,
    val franchise: List<SearchResponse> = emptyList(),
    val similarByTags: List<SearchResponse> = emptyList(),
    /** Stable provider+URL keys whose loaded metadata explicitly confirms a future release. */
    val franchiseUpcomingKeys: Set<String> = emptySet(),
)

/**
 * Finds the works shown in the "More" rail of one title page, entirely inside the
 * title's own source.
 *
 * The rail is decoration, so this work is deliberately gentle: it starts only after the
 * page has settled, never runs while any list on the page is scrolling, stops the moment
 * playback starts or the page is left, and loads metadata for only a few candidates. A
 * failure of any provider call just leaves the rail as it was.
 *
 * Every method is called on the main thread (the view model's scope), exactly like the
 * view model that owns this object.
 */
internal class RelatedWorksController(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(DetailsRelatedState())
    val state: StateFlow<DetailsRelatedState> = _state.asStateFlow()

    private var job: Job? = null
    private var resumeJob: Job? = null
    private var started = false
    private var enabled = false
    private var generation = 0L
    private var discoveryAttempted = false
    private var cachedSearchResults: List<SearchResponse> = emptyList()
    private val metadataAttempted = mutableSetOf<String>()
    private val metadataCache = mutableMapOf<String, RelatedCandidate>()

    private var api: MainAPI? = null
    private var response: LoadResponse? = null

    /**
     * The source this page actually belongs to. A legacy entry or a restored back stack
     * can open a page with an empty source name; the owner is then recovered from the
     * url, and every key and filter here must use the recovered name.
     */
    private var sourceName: String = ""

    /**
     * True while any list on the page is being scrolled. Several lists can scroll
     * independently (the page, the rail, a side pane); the tracker keeps the work paused
     * until every one of them is idle.
     */
    private val scrollActivity = ScrollActivityTracker()
    private val userScrolling = MutableStateFlow(false)

    /** A new title is loading: forget everything about the previous one. */
    fun reset() {
        enabled = false
        resumeJob?.cancel()
        resumeJob = null
        stopJob(resetForRetry = false)
        started = false
        discoveryAttempted = false
        cachedSearchResults = emptyList()
        metadataAttempted.clear()
        metadataCache.clear()
        userScrolling.value = scrollActivity.clear()
        api = null
        response = null
        _state.value = DetailsRelatedState()
    }

    /** The title page has loaded; its related works can now be looked up once enabled. */
    fun attach(api: MainAPI, response: LoadResponse) {
        this.api = api
        this.response = response
        this.sourceName = api.name
    }

    /** The title failed to load: nothing related can exist. */
    fun clear() {
        job?.cancel()
        _state.value = DetailsRelatedState()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            resumeJob?.cancel()
            resumeJob = null
            cancel()
        } else if (!userScrolling.value) {
            ensureLoaded()
        }
    }

    /**
     * Called when playback starts or the page goes away. The work is decoration and must
     * not compete with the player's link extraction for the provider's network and CPU.
     */
    fun cancel() {
        enabled = false
        resumeJob?.cancel()
        resumeJob = null
        stopJob(resetForRetry = true)
    }

    /** Track each independently scrollable surface so one idle list cannot resume the work. */
    fun setScrollActivity(source: String, active: Boolean) {
        val scrolling = scrollActivity.setActive(source, active)
        if (userScrolling.value == scrolling) return
        userScrolling.value = scrolling
        if (scrolling) {
            resumeJob?.cancel()
            resumeJob = null
            // If a fling begins after the work has started, stop the in-flight provider
            // call too: merely waiting before the next request still leaves its network
            // and parsing competing with frames.
            stopJob(resetForRetry = true)
        } else if (enabled) {
            // Do not restart provider traffic in the tiny idle gaps between nested scroll
            // events; a short quiet window avoids cancel/restart loops.
            resumeJob?.cancel()
            resumeJob = scope.launch {
                delay(RESUME_QUIET_MS)
                resumeJob = null
                if (enabled && !userScrolling.value) ensureLoaded()
            }
        }
    }

    private fun ensureLoaded() {
        if (!enabled || userScrolling.value || started) return
        val loadedResponse = response ?: return
        val loadedApi = api ?: return
        started = true
        load(loadedApi, loadedResponse)
    }

    private fun stopJob(resetForRetry: Boolean) {
        // Invalidate any coroutine that races with cancellation before it can publish
        // stale rows or clear the loading state of a newer attempt.
        generation += 1L
        // Keep the quick provider-supplied rows if they were already rendered; only the
        // loading flag is cleared when an in-flight enrichment is cancelled.
        val wasRunning = job?.isActive == true
        job?.cancel()
        job = null
        if (wasRunning && resetForRetry) {
            started = false
            _state.value = _state.value.copy(loading = false)
        }
    }

    private suspend fun awaitScrollIdle() {
        // No arbitrary escape hatch: while the user is scrolling, decorative provider work
        // simply waits until scrolling actually stops.
        userScrolling.first { scrolling -> !scrolling }
    }

    private fun load(api: MainAPI, response: LoadResponse) {
        job?.cancel()
        val attempt = ++generation
        _state.value = _state.value.copy(loading = true)
        // Every provider call, including the fallback search, runs inside this coroutine
        // because Engine.search/load are suspend functions guarded off the UI thread.
        job = scope.launch {
            try {
                val selfKey = normalizeUrlKey(response.url)
                // A metaprovider aggregates real sources, so its recommendations may
                // legitimately name another provider; everywhere else the rows stay
                // strictly single-source.
                val includeForeignSources = api is TmdbProvider
                fun usable(item: SearchResponse): Boolean {
                    // A blank name means the extension built the result directly; it came
                    // from this source's own list, so it belongs here.
                    val sameSource = item.apiName.isBlank() || item.apiName == sourceName ||
                        (includeForeignSources && Engine.provider(item.apiName) != null)
                    return sameSource && item.url.isNotBlank() &&
                        normalizeUrlKey(item.url) != selfKey &&
                        !item.name.trim().equals(response.name.trim(), ignoreCase = true)
                }

                val recommendations = withContext(Dispatchers.Default) {
                    response.recommendations.orEmpty()
                        .filter(::usable)
                        .distinctBy { it.apiName to normalizeUrlKey(it.url) }
                        .take(DISCOVERY_LIMIT)
                }

                // First pass, published before any network search: the provider's own
                // related list needs no extra metadata, so the rail can appear on the
                // first frame after the page is ready instead of after the whole fan-out.
                if (recommendations.isNotEmpty()) {
                    val quick = withContext(Dispatchers.Default) {
                        buildRelatedWorkRows(
                            currentApiName = sourceName,
                            currentTitle = response.name,
                            currentTags = response.tags.orEmpty(),
                            candidates = recommendations.map { RelatedCandidate(it, emptyList()) },
                            currentUrl = response.url,
                            includeForeignSources = includeForeignSources,
                        )
                    }
                    if (attempt == generation) {
                        _state.value = DetailsRelatedState(
                            loading = false,
                            franchise = quick.franchise,
                            similarByTags = quick.similarByTags,
                        )
                    }
                }

                // Never start provider traffic while the user is actively scrolling.
                awaitScrollIdle()

                // A title that itself carries a part marker ("Title Part 2") only searches
                // its own full name, which usually returns just that part. Search the base
                // name as well so the other parts can be found and offered as continuations.
                val titleParts = parseTitleParts(response.name)
                val baseQuery = titleParts.base.joinToString(" ").trim()
                    .takeIf { it.length >= 2 && titleParts.markerKind != null }
                // Subtitle-style franchise releases ("Pirates of the Caribbean: ...") have
                // no numeric part marker; a clear, distinctive prefix is still positive
                // evidence for one bounded sibling search.
                val subtitleQuery = franchisePrefixQuery(response.name)

                val franchiseRecommendations = withContext(Dispatchers.Default) {
                    recommendations.count { candidate -> looksLikeSameFranchise(response.name, candidate.name) }
                }
                // Search only when the source or title gives positive evidence of a
                // multi-part work. An empty recommendation list is NOT a reason to search:
                // for a standalone film that used to issue an unnecessary search on every
                // page open.
                val allowRootTitleSearch = response is MovieLoadResponse || response is AnimeLoadResponse
                val shouldSearch = shouldSearchRelatedCandidates(
                    franchiseRecommendationCount = franchiseRecommendations,
                    hasExplicitPartMarker = baseQuery != null,
                    hasSubtitleFranchisePrefix = subtitleQuery != null,
                    allowRootTitleSearch = allowRootTitleSearch,
                )
                // Root movies and anime also get one bounded exact-title discovery attempt.
                // Results are admitted only after strict franchise matching; generic series
                // still require evidence from the title or the provider's recommendations.
                val discoveryQuery = baseQuery ?: subtitleQuery
                    ?: response.name.takeIf { franchiseRecommendations > 0 || allowRootTitleSearch }
                val searchResults = if (!discoveryQuery.isNullOrBlank() && shouldSearch) {
                    if (discoveryAttempted) {
                        cachedSearchResults
                    } else {
                        val searchResult = try {
                            withTimeoutOrNull(SEARCH_TIMEOUT_MS) { Engine.search(api, discoveryQuery) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        if (attempt != generation) return@launch
                        discoveryAttempted = true
                        cachedSearchResults = searchResult?.getOrNull().orEmpty().take(DISCOVERY_LIMIT)
                        cachedSearchResults
                    }
                } else emptyList()

                // Keep every source-provided recommendation for the franchise rows, while
                // enriching only a small prefix with tags to keep provider traffic bounded.
                val (usableRecommendations, usableSearchResults) = withContext(Dispatchers.Default) {
                    recommendations.filter(::usable) to searchResults.filter(::usable)
                }
                val candidateItems = withContext(Dispatchers.Default) {
                    (usableRecommendations + usableSearchResults)
                        .distinctBy { it.apiName to normalizeUrlKey(it.url) }
                }
                if (candidateItems.isEmpty()) {
                    // The first-pass rows, if any, are still valid; an empty enrichment
                    // search must not make the rail disappear.
                    if (attempt == generation) _state.value = _state.value.copy(loading = false)
                    return@launch
                }
                // A candidate load is useful only if the current work has genre evidence to
                // compare. Format-only or absent tags can never produce a verified
                // similar-by-tags result, so no provider metadata loads are spent on them.
                val candidatesToLoad = if (hasComparableGenreTags(response.tags.orEmpty())) {
                    candidatesForRelatedMetadata(
                        currentTitle = response.name,
                        recommendations = usableRecommendations,
                        searchResults = usableSearchResults,
                    )
                } else emptyList()

                val loaded = arrayOfNulls<RelatedCandidate>(candidatesToLoad.size)
                // Two provider loads at a time: the small candidate cap at the per-load
                // limit completes in at most two bounded waves when nothing is cached.
                val gate = Semaphore(2)
                coroutineScope {
                    candidatesToLoad.forEachIndexed { index, item ->
                        launch {
                            gate.withPermit {
                                awaitScrollIdle()
                                val metadataKey = "${item.apiName}|${normalizeUrlKey(item.url)}"
                                metadataCache[metadataKey]?.let { cached ->
                                    loaded[index] = cached.copy(item = item)
                                    return@withPermit
                                }
                                if (metadataKey in metadataAttempted) return@withPermit

                                val itemApi = Engine.provider(item.apiName) ?: api
                                val result = try {
                                    withTimeoutOrNull(METADATA_TIMEOUT_MS) { Engine.load(itemApi, item.url) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                                if (attempt != generation) return@withPermit
                                metadataAttempted += metadataKey
                                val metadata = result?.getOrNull() ?: return@withPermit
                                if (item.posterUrl.isNullOrBlank()) {
                                    metadata.posterUrl?.takeIf(String::isNotBlank)?.let { item.posterUrl = it }
                                }
                                if (item.posterHeaders.isNullOrEmpty()) {
                                    metadata.posterHeaders?.takeIf { it.isNotEmpty() }?.let { item.posterHeaders = it }
                                }
                                val enriched = RelatedCandidate(
                                    item = item,
                                    tags = metadata.tags.orEmpty(),
                                    verifiedUpcoming = hasExplicitUpcomingMetadata(
                                        metadata,
                                        System.currentTimeMillis() / 1000L,
                                    ),
                                )
                                metadataCache[metadataKey] = enriched
                                loaded[index] = enriched
                            }
                        }
                    }
                }

                if (attempt != generation) return@launch
                if (Engine.provider(sourceName) !== api) {
                    // The source was reloaded while the work ran; its results are stale.
                    _state.value = DetailsRelatedState()
                    return@launch
                }
                val loadedByIdentity = loaded.filterNotNull()
                    .associateBy { it.item.apiName to normalizeUrlKey(it.item.url) }
                val enrichedCandidates = candidateItems.map { item ->
                    loadedByIdentity[item.apiName to normalizeUrlKey(item.url)]
                        ?: RelatedCandidate(item, emptyList())
                }
                val rows = withContext(Dispatchers.Default) {
                    buildRelatedWorkRows(
                        currentApiName = sourceName,
                        currentTitle = response.name,
                        currentTags = response.tags.orEmpty(),
                        candidates = enrichedCandidates,
                        currentUrl = response.url,
                        includeForeignSources = includeForeignSources,
                    )
                }
                val candidatesByKey = enrichedCandidates.associateBy { candidate ->
                    "${candidate.item.apiName}|${normalizeUrlKey(candidate.item.url)}"
                }
                val verifiedUpcomingKeys = rows.franchise.mapNotNull { item ->
                    val key = "${item.apiName}|${normalizeUrlKey(item.url)}"
                    key.takeIf { candidatesByKey[key]?.verifiedUpcoming == true }
                }.toSet()
                if (attempt == generation) {
                    _state.value = DetailsRelatedState(
                        loading = false,
                        franchise = rows.franchise,
                        similarByTags = rows.similarByTags,
                        franchiseUpcomingKeys = verifiedUpcomingKeys,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The rail is optional. A provider or parser failure must never leave its
                // spinner active or take down the already-loaded title page.
                if (attempt == generation && enabled) _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private companion object {
        const val RESUME_QUIET_MS = 650L
        const val DISCOVERY_LIMIT = 100
        const val SEARCH_TIMEOUT_MS = 4_500L
        const val METADATA_TIMEOUT_MS = 3_500L
    }
}
