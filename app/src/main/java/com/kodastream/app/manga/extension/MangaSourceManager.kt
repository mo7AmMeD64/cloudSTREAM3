package com.kodastream.app.manga.extension

import android.content.Context
import com.kodastream.app.manga.store.ExtensionStore
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Single owner of loaded extensions and their sources. Part 11 reads [sources]. */
class MangaSourceManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val loader = ExtensionLoader(appContext)
    private val installer = ExtensionInstaller(appContext)
    private val started = AtomicBoolean(false)

    private val _extensions = MutableStateFlow<List<MangaExtension.Installed>>(emptyList())
    val extensions: StateFlow<List<MangaExtension.Installed>> = _extensions.asStateFlow()

    private val _sources = MutableStateFlow<List<LoadedSource>>(emptyList())
    val sources: StateFlow<List<LoadedSource>> = _sources.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun ensureLoaded(stores: () -> List<ExtensionStore>) {
        if (started.get()) return
        reload(stores())
    }

    suspend fun reload(stores: List<ExtensionStore>) = withContext(Dispatchers.IO) {
        _loading.value = true
        _error.value = null
        try {
            val loaded = loader.loadAll(stores)
            _extensions.value = loaded.sortedBy { it.name.lowercase() }
            _sources.value = loaded
                .filterIsInstance<MangaExtension.Loaded>()
                .flatMap { extension ->
                    extension.sources.map { source ->
                        LoadedSource(
                            source = source,
                            extension = extension,
                            language = source.lang.ifBlank { extension.lang.orEmpty() },
                        )
                    }
                }
                .sortedBy { it.source.name.lowercase() }
            started.set(true)
        } catch (e: Throwable) {
            _error.value = e.message ?: "Could not load extensions"
        } finally {
            _loading.value = false
        }
    }

    suspend fun uninstall(pkgName: String, stores: List<ExtensionStore>) {
        installer.uninstall(pkgName)
        reload(stores)
    }

    fun trust(pkgName: String, versionCode: Long, signature: String) {
        installer.trust(pkgName, versionCode, signature)
    }

    fun sourceById(id: Long): Source? = _sources.value.firstOrNull { it.source.id == id }?.source

    fun catalogueSourceById(id: Long): CatalogueSource? = sourceById(id) as? CatalogueSource

    fun sourceByName(name: String): Source? = _sources.value.firstOrNull { it.source.name == name }?.source

    fun getOrStub(id: Long): Source = sourceById(id) ?: StubSource(id)

    companion object {
        @Volatile
        private var instance: MangaSourceManager? = null

        fun get(context: Context): MangaSourceManager =
            instance ?: synchronized(this) {
                instance ?: MangaSourceManager(context).also { instance = it }
            }
    }
}

/** Placeholder for a source id that a removed or updated extension used to provide. */
class StubSource(
    override val id: Long,
    override val name: String = "Unknown source",
) : CatalogueSource {
    override val lang: String = ""
    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage =
        throw UnsupportedOperationException("This source is no longer installed")

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException("This source is no longer installed")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw UnsupportedOperationException("This source is no longer installed")

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw UnsupportedOperationException("This source is no longer installed")

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException("This source is no longer installed")
}
