package com.kodastream.app.ui.manga

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.R
import com.kodastream.app.manga.extension.ExtensionInstaller
import com.kodastream.app.manga.extension.InstallFailureStep
import com.kodastream.app.manga.extension.InstallStep
import com.kodastream.app.manga.extension.MangaExtension
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.extension.MangaSourceTestState
import com.kodastream.app.manga.extension.MangaSourceTester
import com.kodastream.app.manga.extension.clearMangaSourcePreferences
import com.kodastream.app.manga.store.AvailableExtension
import com.kodastream.app.manga.store.ExtensionStore
import com.kodastream.app.manga.store.MangaStoreRepository
import com.kodastream.app.manga.store.StoreResult
import eu.kanade.tachiyomi.compat.MangaEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns the Extensions screen's extension, store and install state. */
class MangaExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val manager = MangaSourceManager.get(context)
    private val storeRepository = MangaStoreRepository.get(context)
    private val installer = ExtensionInstaller(context)

    val installed: StateFlow<List<MangaExtension.Installed>> = manager.extensions
    val sources = manager.sources
    val loading: StateFlow<Boolean> = manager.loading
    val available: StateFlow<List<AvailableExtension>> = storeRepository.available
    val stores: StateFlow<List<ExtensionStore>> = storeRepository.stores
    val refreshing: StateFlow<Boolean> = storeRepository.refreshing
    val storeError: StateFlow<String?> = storeRepository.error
    val lastRefresh: StateFlow<Long> = storeRepository.lastRefresh
    private val tester by lazy { MangaSourceTester(MangaEngine.get(context)) }

    /** Per package: the install step shown on its row until the extension reloads. */
    private val _installing = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installing: StateFlow<Map<String, InstallStep>> = _installing.asStateFlow()

    private val _testingSourceId = MutableStateFlow<Long?>(null)
    val testingSourceId: StateFlow<Long?> = _testingSourceId.asStateFlow()

    init {
        viewModelScope.launch {
            // Manga mode was opened for the first time: load installed extensions and the index.
            storeRepository.ensureDefaultStore()
            manager.ensureLoaded { storeRepository.stores.value }
            storeRepository.refreshIfStale()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            storeRepository.refreshAll(force = true)
            manager.reload(storeRepository.stores.value)
            clearResolvedLoadErrors()
        }
    }

    fun reloadExtensions() {
        viewModelScope.launch {
            manager.reload(storeRepository.stores.value)
            clearResolvedLoadErrors()
        }
    }

    fun install(extension: AvailableExtension) {
        val current = _installing.value[extension.pkgName]
        if (current != null && current !is InstallStep.Error) return
        viewModelScope.launch {
            installer.install(extension).collect { step ->
                _installing.update { it + (extension.pkgName to step) }
                when (step) {
                    is InstallStep.Installed -> {
                        manager.reloadPackage(extension.pkgName, storeRepository.stores.value)
                        val loaded = manager.extensions.value
                            .firstOrNull { it.pkgName == extension.pkgName }
                        if (loaded is MangaExtension.NotLoaded) {
                            _installing.update { it + (extension.pkgName to loadError(loaded)) }
                        } else {
                            _installing.update { it - extension.pkgName }
                            toast(R.string.manga_installed_ok, extension.name)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun update(extension: MangaExtension.Installed) {
        val listing = extension.findUpdate(available.value) ?: return
        install(listing)
    }

    fun updateAll() {
        viewModelScope.launch {
            val updates = installed.value
                .filterIsInstance<MangaExtension.Loaded>()
                .mapNotNull { it.findUpdate(available.value) }
            if (updates.isEmpty()) return@launch
            updates.forEach { extension ->
                installer.install(extension).collect { step ->
                    _installing.update { it + (extension.pkgName to step) }
                    if (step is InstallStep.Installed) {
                        manager.reloadPackage(extension.pkgName, storeRepository.stores.value)
                        val loaded = manager.extensions.value
                            .firstOrNull { it.pkgName == extension.pkgName }
                        if (loaded is MangaExtension.NotLoaded) {
                            _installing.update { it + (extension.pkgName to loadError(loaded)) }
                        } else {
                            _installing.update { it - extension.pkgName }
                        }
                    }
                }
            }
            clearResolvedLoadErrors()
        }
    }

    fun uninstall(extension: MangaExtension.Installed, clearData: Boolean) {
        viewModelScope.launch {
            val sourceIds = sources.value
                .filter { it.extension.pkgName == extension.pkgName }
                .map { it.source.id }
            manager.uninstall(extension.pkgName, storeRepository.stores.value)
            _installing.update { it - extension.pkgName }
            if (clearData) sourceIds.forEach { clearMangaSourcePreferences(context, it) }
            if (clearData) toast(R.string.manga_extension_data_cleared)
        }
    }

    fun trust(extension: MangaExtension.NotLoaded) {
        val reason = extension.reason as? MangaExtension.NotLoaded.Reason.Untrusted ?: return
        manager.trust(extension.pkgName, extension.versionCode, reason.signature)
        reloadExtensions()
    }

    fun test(sourceId: Long): Flow<MangaSourceTestState>? =
        manager.sourceById(sourceId)?.let { tester.run(it) }

    fun startTest(sourceId: Long) {
        _testingSourceId.value = sourceId
    }

    fun stopTest() {
        _testingSourceId.value = null
    }

    fun addStore(url: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            when (val result = storeRepository.addStore(url)) {
                is StoreResult.Success -> {
                    manager.reload(storeRepository.stores.value)
                    onDone(null)
                }
                is StoreResult.NetworkError -> onDone(result.message)
                is StoreResult.InvalidFormat -> onDone(result.message)
                StoreResult.NotModified -> onDone(null)
            }
        }
    }

    fun removeStore(store: ExtensionStore) {
        viewModelScope.launch {
            storeRepository.removeStore(store)
            manager.reload(storeRepository.stores.value)
        }
    }

    fun refreshStores() {
        viewModelScope.launch { storeRepository.refreshAll(force = true) }
    }

    private fun loadError(extension: MangaExtension.NotLoaded): InstallStep.Error {
        val failed = extension.reason as? MangaExtension.NotLoaded.Reason.Failed
        return InstallStep.Error(
            message = failed?.message ?: extension.reason.toString(),
            step = InstallFailureStep.Load,
            name = extension.name,
            pkgName = extension.pkgName,
            versionName = extension.versionName,
            stackTrace = failed?.stackTrace.orEmpty(),
        )
    }

    /** A successful reload clears an old load error without touching install errors. */
    private fun clearResolvedLoadErrors() {
        val loaded = manager.extensions.value
            .filterIsInstance<MangaExtension.Loaded>()
            .map { it.pkgName }
            .toSet()
        _installing.update { steps ->
            steps.filterNot { (pkgName, step) ->
                step is InstallStep.Error &&
                    step.step == InstallFailureStep.Load &&
                    pkgName in loaded
            }
        }
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(context, context.getString(resId, *args), Toast.LENGTH_LONG).show()
    }
}
