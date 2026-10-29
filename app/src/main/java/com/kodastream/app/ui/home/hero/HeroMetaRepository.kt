/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/HomeHeroMetaRepository.kt
 */
package com.kodastream.app.ui.home.hero

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Plain values, so [withHeroMeta] stays testable without engine classes. */
internal data class HomeHeroMeta(
    val banner: String? = null,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val releaseInfo: String? = null,
    val status: String? = null,
    val ageRating: String? = null,
    val imdbRating: String? = null,
)

internal object HomeHeroMetaRepository {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val _metas = MutableStateFlow<Map<String, HomeHeroMeta>>(emptyMap())
    val metas: StateFlow<Map<String, HomeHeroMeta>> = _metas.asStateFlow()
    private val cache = HeroMetaCache()

    suspend fun load(provider: String, items: List<HeroItem>) {
        val keys = items.mapTo(mutableSetOf(), HeroItem::stableKey)
        _metas.update { metas -> metas.filterKeys(keys::contains) }
        val permits = Semaphore(DETAILS_FETCH_CONCURRENCY)
        withContext(dispatcher) {
            items.forEach { item ->
                launch { loadItem(provider, item, permits) }
            }
        }
    }

    private suspend fun loadItem(provider: String, item: HeroItem, permits: Semaphore) {
        val key = item.stableKey()
        val cacheKey = cacheKey(provider, item)
        cache.get(cacheKey)?.let { cached ->
            update(key, cached)
            return
        }
        val meta = permits.withPermit { fetchMeta(provider, item, cacheKey) } ?: return
        update(key, meta)
    }

    private fun update(key: String, meta: HomeHeroMeta) {
        _metas.update { metas -> metas + (key to meta) }
    }

    private suspend fun fetchMeta(
        provider: String,
        item: HeroItem,
        cacheKey: String,
    ): HomeHeroMeta? {
        val api = Engine.provider(provider) ?: return null
        return try {
            val meta = withTimeoutOrNull(META_TIMEOUT_MS) {
                Engine.load(api, item.id)
                    .getOrNull()
                    ?.let { response ->
                        loadResponseToHeroMeta(response, isSeries = item.type == "series")
                    }
            }
            meta?.also { cache.put(cacheKey, it) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // A failed item keeps its poster-only look.
            null
        }
    }
}

private fun cacheKey(provider: String, item: HeroItem): String = "$provider|${item.id}"

internal fun loadResponseToHeroMeta(
    response: LoadResponse,
    isSeries: Boolean,
): HomeHeroMeta {
    val showStatus = if (isSeries) {
        when (response) {
            is TvSeriesLoadResponse -> response.showStatus
            is AnimeLoadResponse -> response.showStatus
            else -> null
        }
    } else {
        null
    }
    return heroMetaFromValues(
        banner = response.backgroundPosterUrl,
        logo = response.logoUrl,
        genres = response.tags,
        duration = response.duration,
        year = response.year,
        status = showStatus?.let { if (it == ShowStatus.Completed) "ended" else "continuing" },
        ageRating = response.contentRating,
        score = response.score?.toDouble(10),
    )
}

/** The pure part of [loadResponseToHeroMeta], so tests need no engine classes. */
internal fun heroMetaFromValues(
    banner: String? = null,
    logo: String? = null,
    genres: List<String>? = null,
    duration: Int? = null,
    year: Int? = null,
    status: String? = null,
    ageRating: String? = null,
    score: Double? = null,
): HomeHeroMeta = HomeHeroMeta(
    banner = banner?.takeIf { it.isNotBlank() },
    logo = logo?.takeIf { it.isNotBlank() },
    genres = genres.orEmpty().filter { it.isNotBlank() },
    runtime = duration?.takeIf { it > 0 }?.toString(),
    releaseInfo = year?.toString(),
    status = status?.takeIf { it.isNotBlank() },
    ageRating = ageRating?.takeIf { it.isNotBlank() },
    imdbRating = score?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) },
)

internal fun HeroItem.withHeroMeta(meta: HomeHeroMeta): HeroItem = copy(
    banner = meta.banner?.takeIf { it.isNotBlank() } ?: banner,
    logo = meta.logo?.takeIf { it.isNotBlank() } ?: logo,
    genres = meta.genres.takeIf { it.isNotEmpty() } ?: genres,
    imdbRating = meta.imdbRating?.takeIf { it.isNotBlank() } ?: imdbRating,
    runtime = meta.runtime?.takeIf { it.isNotBlank() } ?: runtime,
    releaseInfo = meta.releaseInfo?.takeIf { it.isNotBlank() } ?: releaseInfo,
    status = meta.status?.takeIf { it.isNotBlank() } ?: status,
    ageRating = meta.ageRating?.takeIf { it.isNotBlank() } ?: ageRating,
)

/**
 * Nuvio's lazy enrichment: wait for scrolling to settle, then load the details
 * of the visible titles. Cancelled by the caller leaving Home or changing provider.
 */
@Composable
internal fun rememberHomeHeroState(
    items: List<HeroItem>,
    provider: String?,
): List<HeroItem> {
    val metas by HomeHeroMetaRepository.metas.collectAsState()

    // ScreenActivityEffect cancels the enrichment when Home is left or the provider changes.
    ScreenActivityEffect(provider, items) { active ->
        if (!active || provider == null) return@ScreenActivityEffect
        delay(LOAD_DEBOUNCE_MS)
        HomeHeroMetaRepository.load(provider, items)
    }

    return remember(items, metas) {
        items.map { item -> metas[item.stableKey()]?.let(item::withHeroMeta) ?: item }
    }
}

/** In-memory only, capped so a long session cannot grow it without bound. */
internal class HeroMetaCache(private val maxEntries: Int = HERO_META_CACHE_LIMIT) {
    private val entries = LinkedHashMap<String, HomeHeroMeta>()

    val size: Int get() = entries.size

    fun get(key: String): HomeHeroMeta? = entries[key]

    fun put(key: String, value: HomeHeroMeta) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }
}

internal const val HERO_META_CACHE_LIMIT = 32
private const val DETAILS_FETCH_CONCURRENCY = 3
private const val META_TIMEOUT_MS = 5_000L
private const val LOAD_DEBOUNCE_MS = 400L
