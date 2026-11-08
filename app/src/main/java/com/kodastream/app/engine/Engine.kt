package com.kodastream.app.engine

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The one place where the UI talks to extension code.
 *
 * Extensions are third-party code. They throw, hang, return nothing and
 * sometimes do not implement a method at all (NotImplementedError is an Error,
 * not an Exception). Every call here is time-limited, runs off the main thread
 * and comes back as a [Result], so a broken extension can never take a screen
 * down with it.
 */
object Engine {
    private const val DEFAULT_TIMEOUT_MS = 60_000L

    /** One home page: its lists and whether the provider has another page. */
    data class MainPageResult(val lists: List<HomePageList>, val hasNext: Boolean)

    /** Every provider that loaded successfully, in registration order. */
    fun providers(): List<MainAPI> =
        APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }

    fun provider(name: String?): MainAPI? = APIHolder.getApiFromNameNull(name)

    /**
     * Last-resort provider resolution for a page whose source name was lost: a saved
     * entry from an older build or a restored back stack can carry an empty `apiName`.
     * The page url still starts with the provider's site, so the owner is recovered from
     * it instead of failing the screen with `Provider "" is not loaded`.
     */
    fun providerForUrl(url: String?): MainAPI? = APIHolder.getApiFromUrlNull(url)

    /** Providers that can show a home page of their own. */
    fun homeProviders(): List<MainAPI> = providers().filter { it.hasMainPage }

    /** A [Result] for a call into extension code. Cancellation still cancels. */
    suspend fun <T> guarded(timeoutMs: Long?, block: suspend () -> T): Result<T> {
        return try {
            Result.success(withContext(Dispatchers.IO) { withTimeout(timeoutMs ?: DEFAULT_TIMEOUT_MS) { block() } })
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logError(t)
            Result.failure(t)
        }
    }

    /**
     * Fills in the owning provider on results that do not declare one, and drops results
     * that have no url (they could never be opened).
     *
     * A CloudStream extension builds its results either with the `MainAPI.new*SearchResponse`
     * helpers, which set `apiName`, or with a direct constructor, which leaves it empty;
     * many Arabic extensions do the latter. An empty `apiName` is not cosmetic: the details
     * route resolves the provider from it, so such a card opened nothing, and related-title
     * matching compared `""` with the real provider name and threw every candidate away.
     *
     * Stamping here, at the one boundary where extension code is called, makes the source
     * unambiguous for every caller without touching a single extension. A non-blank
     * `apiName` is left alone: a metaprovider legitimately points at sub-providers, which
     * must keep their own name so their pages can still be loaded.
     */
    private fun <T : SearchResponse> stampSource(api: MainAPI, items: List<T>): List<T> =
        stampResults(api.name, items)

    suspend fun mainPage(api: MainAPI, page: MainPageData, pageNumber: Int = 1): Result<MainPageResult> =
        guarded(api.getMainPageTimeoutMs) {
            val request = MainPageRequest(page.name, page.data, page.horizontalImages)
            val response = api.getMainPage(pageNumber, request)
            // Home rows are HomePageList buckets, each holding its own items.
            val lists = response?.items.orEmpty().map { row ->
                row.copy(list = stampSource(api, row.list))
            }
            MainPageResult(lists, response?.hasNext == true)
        }

    suspend fun search(api: MainAPI, query: String): Result<List<SearchResponse>> =
        guarded(api.searchTimeoutMs) {
            stampSource(api, api.search(query, 1)?.items ?: emptyList())
        }

    suspend fun load(api: MainAPI, url: String): Result<LoadResponse> =
        guarded(api.loadTimeoutMs) {
            val response = api.load(url)
                ?: throw IllegalStateException("The provider returned nothing for this page")
            response.apiName = sourceNameFor(response.apiName, api.name)
            // Recommendations feed the "More" row; stamping them here is what lets it be
            // limited to this one source.
            response.recommendations = response.recommendations?.let { stampSource(api, it) }
            response
        }
}

/** A short message a person can read, whatever the exception was. */
fun Throwable.readableMessage(): String {
    if (this is TimeoutCancellationException) return "Timed out"
    if (this is NotImplementedError) return "Not supported by this provider"
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
