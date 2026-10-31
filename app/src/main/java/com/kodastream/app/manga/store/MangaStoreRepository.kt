package com.kodastream.app.manga.store

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.compat.MangaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores the user's extension stores and the last parsed available list (JSON on disk),
 * so the Available tab opens offline and instantly.
 */
class MangaStoreRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("manga_store", Context.MODE_PRIVATE)
    private val json = MangaEngine.get(appContext).json
    private val cacheFile = File(appContext.filesDir, "manga/available.json")
    private val mutex = Mutex()

    private val _stores = MutableStateFlow(loadStores())
    val stores: StateFlow<List<ExtensionStore>> = _stores.asStateFlow()

    private val _available = MutableStateFlow(loadCache().flatMap { it.extensions })
    val available: StateFlow<List<AvailableExtension>> = _available.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastRefresh = MutableStateFlow(loadCache().maxOfOrNull { it.fetchedAt } ?: 0L)
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private fun service(): ExtensionStoreService? {
        val engine = MangaEngine.get(appContext)
        val network = engine.network ?: return null
        return ExtensionStoreService(network, engine.json, engine.protoBuf)
    }

    suspend fun ensureDefaultStore() {
        if (_stores.value.isEmpty()) {
            _stores.value = listOf(
                ExtensionStore(
                    indexUrl = KEIYOUSHI_URL,
                    name = "Keiyoushi",
                    badgeLabel = "Keiyoushi",
                    signingKey = "",
                ),
            )
            saveStores(_stores.value)
        }
    }

    suspend fun refreshIfStale() {
        val oldest = loadCache().minOfOrNull { it.fetchedAt } ?: 0L
        if (System.currentTimeMillis() - oldest > REFRESH_INTERVAL_MS) refreshAll(force = false)
    }

    suspend fun refreshAll(force: Boolean = false) = mutex.withLock {
        if (_refreshing.value) return@withLock
        _refreshing.value = true
        _error.value = null
        try {
            val stores = _stores.value
            if (stores.isEmpty()) return@withLock
            val cache = loadCache().associateBy { it.storeUrl }.toMutableMap()
            var lastError: String? = null
            for (store in stores) {
                val entry = cache[store.indexUrl]
                if (!force && entry != null && System.currentTimeMillis() - entry.fetchedAt < REFRESH_INTERVAL_MS) {
                    continue
                }
                when (val result = service()?.fetch(store.indexUrl, entry?.etag, entry?.lastModified)) {
                    is StoreResult.Success -> {
                        cache[store.indexUrl] = StoreCacheEntry(
                            storeUrl = store.indexUrl,
                            etag = result.etag,
                            lastModified = result.lastModified,
                            fetchedAt = System.currentTimeMillis(),
                            extensions = result.extensions,
                        )
                        if (result.store != store) {
                            _stores.value = _stores.value.map {
                                if (it.indexUrl == store.indexUrl) result.store else it
                            }
                            saveStores(_stores.value)
                        }
                    }
                    StoreResult.NotModified -> {
                        cache[store.indexUrl] = entry!!.copy(fetchedAt = System.currentTimeMillis())
                    }
                    is StoreResult.NetworkError -> lastError = result.message
                    is StoreResult.InvalidFormat -> lastError = result.message
                    null -> lastError = MangaEngine.get(appContext).error
                }
            }
            saveCache(cache.values.toList())
            _available.value = cache.values.flatMap { it.extensions }.sortedBy { it.name.lowercase() }
            _lastRefresh.value = cache.values.maxOfOrNull { it.fetchedAt } ?: 0L
            _error.value = lastError
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun addStore(url: String): StoreResult = mutex.withLock {
        val storeService = service()
            ?: return@withLock StoreResult.InvalidFormat(
                MangaEngine.get(appContext).error ?: "The manga engine is not available",
            )
        val result = storeService.fetch(url)
        if (result is StoreResult.Success) {
            if (result.store.signingKey.isBlank()) {
                return@withLock StoreResult.InvalidFormat("The store has no signing key")
            }
            val normalized = result.store.copy(
                indexUrl = normalizeStoreUrl(result.store.indexUrl),
            )
            _stores.value = (_stores.value.filterNot { it.indexUrl == normalized.indexUrl } + normalized)
            saveStores(_stores.value)
            val cache = loadCache().filterNot { it.storeUrl == normalized.indexUrl } +
                StoreCacheEntry(
                    storeUrl = normalized.indexUrl,
                    etag = result.etag,
                    lastModified = result.lastModified,
                    fetchedAt = System.currentTimeMillis(),
                    extensions = result.extensions,
                )
            saveCache(cache)
            _available.value = cache.flatMap { it.extensions }.sortedBy { it.name.lowercase() }
            _lastRefresh.value = System.currentTimeMillis()
        }
        result
    }

    suspend fun removeStore(store: ExtensionStore) = mutex.withLock {
        _stores.value = _stores.value.filterNot { it.indexUrl == store.indexUrl }
        saveStores(_stores.value)
        val cache = loadCache().filterNot { it.storeUrl == store.indexUrl }
        saveCache(cache)
        _available.value = cache.flatMap { it.extensions }.sortedBy { it.name.lowercase() }
    }

    fun storeFor(url: String): ExtensionStore? = _stores.value.firstOrNull { it.indexUrl == url }

    private fun loadStores(): List<ExtensionStore> {
        val raw = prefs.getString(KEY_STORES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ExtensionStore.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun saveStores(stores: List<ExtensionStore>) {
        prefs.edit().putString(
            KEY_STORES,
            json.encodeToString(ListSerializer(ExtensionStore.serializer()), stores),
        ).apply()
    }

    private fun loadCache(): List<StoreCacheEntry> {
        if (!cacheFile.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoreCacheEntry.serializer()), cacheFile.readText())
        }.getOrDefault(emptyList())
    }

    private suspend fun saveCache(entries: List<StoreCacheEntry>) = withContext(Dispatchers.IO) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(
            json.encodeToString(ListSerializer(StoreCacheEntry.serializer()), entries),
        )
    }

    companion object {
        const val KEIYOUSHI_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"
        private const val KEY_STORES = "stores"
        private const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000

        @Volatile
        private var instance: MangaStoreRepository? = null

        fun get(context: Context): MangaStoreRepository =
            instance ?: synchronized(this) {
                instance ?: MangaStoreRepository(context).also { instance = it }
            }

        fun normalizeStoreUrl(url: String): String {
            val trimmed = url.trim()
            return when {
                trimmed.endsWith("/index.min.json") -> trimmed.removeSuffix("index.min.json") + "repo.json"
                else -> trimmed
            }
        }
    }
}
