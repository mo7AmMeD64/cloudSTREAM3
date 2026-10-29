package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.removePluginMapping
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.RepositoryManager.ONLINE_PLUGINS_FOLDER
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

/**
 * Downloads, loads and unloads extensions (.cs3 files).
 *
 * A .cs3 is a DEX file with a manifest.json. It is loaded with a PathClassLoader
 * whose parent is this app's class loader, so the extension finds the engine
 * classes (MainAPI, ExtractorLink, ...) that live in the :library module.
 * That is the whole trick that makes CloudStream extensions work here.
 */
object PluginManager {
    const val TAG = "PluginManager"

    // One writer at a time: installs, updates and removals touch files and the stored list.
    private val lock = Mutex()

    /** filePath -> loaded plugin instance */
    private val plugins = HashMap<String, BasePlugin>()

    private val _revision = MutableStateFlow(0)

    /** Increments whenever the set of loaded providers or installed plugins changes. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _startupDone = MutableStateFlow(false)

    /** True once the plugins that were already installed have been loaded at startup. */
    val startupDone: StateFlow<Boolean> = _startupDone.asStateFlow()

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    fun getInstalled(context: Context): List<InstalledPlugin> = ExtensionStore.installed(context)

    fun getRepositories(context: Context): List<RepositoryData> = ExtensionStore.repositories(context)

    fun isLoaded(filePath: String): Boolean = synchronized(plugins) { plugins.containsKey(filePath) }

    fun getPlugin(filePath: String): BasePlugin? = synchronized(plugins) { plugins[filePath] }

    /** Spits out a unique and safe file name based on [name]. */
    fun getPluginSanitizedFileName(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60) + "." + name.hashCode()
    }

    fun getPluginPath(context: Context, internalName: String, repositoryUrl: String): File {
        val folderName = getPluginSanitizedFileName(repositoryUrl)
        val fileName = getPluginSanitizedFileName(internalName)
        return File("${context.filesDir}/$ONLINE_PLUGINS_FOLDER/$folderName/$fileName.cs3")
    }

    // ---------------------------------------------------------------- repositories

    suspend fun addRepository(context: Context, repo: RepositoryData) {
        lock.withLock {
            val current = ExtensionStore.repositories(context)
            ExtensionStore.saveRepositories(context, (current + repo).distinctBy { it.url })
        }
        bump()
    }

    /** Removes the repository and every plugin that came from it. */
    suspend fun removeRepository(context: Context, repo: RepositoryData) {
        lock.withLock {
            val installed = ExtensionStore.installed(context)
            val (fromRepo, others) = installed.partition { it.repoUrl == repo.url }
            fromRepo.forEach { unloadPlugin(it.filePath) }
            withContext(Dispatchers.IO) {
                fromRepo.forEach { deleteFile(File(it.filePath)) }
            }
            ExtensionStore.saveInstalled(context, others)
            ExtensionStore.saveRepositories(context, ExtensionStore.repositories(context).filter { it.url != repo.url })
        }
        bump()
    }

    // ---------------------------------------------------------------- install / update / remove

    /** Downloads [site] from [repo] and loads it. Returns an error message, or null on success. */
    suspend fun install(context: Context, repo: RepositoryData, site: SitePlugin): String? {
        return lock.withLock {
            val file = getPluginPath(context, site.internalName, repo.url)
            // Unload the old copy first, a loaded dex file cannot be replaced safely.
            unloadPlugin(file.absolutePath)

            val error = RepositoryManager.downloadPluginToFile(context, site.url, file, site.fileHash)
            if (error != null) return@withLock error

            val record = InstalledPlugin(
                internalName = site.internalName,
                name = site.name,
                repoUrl = repo.url,
                downloadUrl = site.url,
                version = site.version,
                filePath = file.absolutePath,
                iconUrl = site.iconUrl,
            )
            val others = ExtensionStore.installed(context).filter { it.filePath != record.filePath }
            ExtensionStore.saveInstalled(context, others + record)

            val loadError = loadPlugin(context, file)
            APIHolder.initAll()
            bump()
            loadError
        }
    }

    suspend fun uninstall(context: Context, plugin: InstalledPlugin) {
        lock.withLock {
            unloadPlugin(plugin.filePath)
            withContext(Dispatchers.IO) { deleteFile(File(plugin.filePath)) }
            ExtensionStore.saveInstalled(
                context,
                ExtensionStore.installed(context).filter { it.filePath != plugin.filePath },
            )
        }
        bump()
    }

    /** Loads everything that is already installed. Called once at startup. */
    suspend fun loadAllInstalled(context: Context) {
        lock.withLock {
            val installed = ExtensionStore.installed(context)
            val missing = mutableListOf<InstalledPlugin>()
            for (record in installed) {
                val file = File(record.filePath)
                if (!file.exists()) {
                    missing.add(record)
                    continue
                }
                loadPlugin(context, file)
            }
            if (missing.isNotEmpty()) {
                ExtensionStore.saveInstalled(context, installed - missing.toSet())
            }
            APIHolder.initAll()
        }
        _startupDone.value = true
        bump()
    }

    /**
     * Checks every repository for a newer version of each installed plugin and
     * updates it. Returns how many plugins were updated.
     */
    suspend fun updateAll(context: Context): Int {
        var updated = 0
        val repos = ExtensionStore.repositories(context)
        for (repo in repos) {
            val available = try {
                RepositoryManager.getRepoPlugins(repo)
            } catch (t: Throwable) {
                logError(t)
                null
            } ?: continue
            val installedFromRepo = ExtensionStore.installed(context).filter { it.repoUrl == repo.url }
            for (record in installedFromRepo) {
                val site = available.firstOrNull { it.internalName == record.internalName } ?: continue
                if (site.version > record.version) {
                    if (install(context, repo, site) == null) updated++
                }
            }
        }
        return updated
    }

    // ---------------------------------------------------------------- loading

    private fun deleteFile(file: File) {
        try {
            // Loaded dex files are marked read-only; clear that before deleting.
            file.setWritable(true)
            file.delete()
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /** Loads one .cs3 file. Returns an error message, or null on success. */
    private fun loadPlugin(context: Context, file: File): String? {
        val filePath = file.absolutePath
        val fileName = file.nameWithoutExtension
        if (isLoaded(filePath)) return null

        return try {
            // Android 14+ refuses to load writable dex files.
            try {
                if (!file.setReadOnly()) Log.e(TAG, "Failed to set read-only on ${file.name}")
            } catch (t: Throwable) {
                logError(t)
            }

            val loader = PathClassLoader(filePath, context.classLoader)
            val manifestText = loader.getResourceAsStream("manifest.json")?.use { stream ->
                InputStreamReader(stream).use { reader -> reader.readText() }
            } ?: return "No manifest.json in $fileName"

            val manifest = JSONObject(manifestText)
            val pluginClassName = manifest.optString("pluginClassName", "")
            if (pluginClassName.isBlank()) return "manifest.json of $fileName has no pluginClassName"
            val requiresResources = manifest.optBoolean("requiresResources", false)

            val pluginClass: Class<*> = loader.loadClass(pluginClassName)
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
            pluginInstance.filename = filePath

            if (requiresResources) {
                // Based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assets, filePath)
                @Suppress("DEPRECATION")
                (pluginInstance as? Plugin)?.resources = Resources(
                    assets,
                    context.resources.displayMetrics,
                    context.resources.configuration,
                )
            }

            synchronized(plugins) { plugins[filePath] = pluginInstance }

            if (pluginInstance is Plugin) {
                pluginInstance.load(context)
            } else {
                pluginInstance.load()
            }
            Log.i(TAG, "Loaded plugin $fileName")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load $file: ${Log.getStackTraceString(t)}")
            // Do not keep a half loaded plugin around.
            unloadPlugin(filePath)
            "Failed to load $fileName: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun unloadPlugin(absolutePath: String) {
        val plugin = synchronized(plugins) { plugins[absolutePath] }
        if (plugin == null) {
            // Even when the instance never finished loading, drop anything it registered.
            removeRegistered(absolutePath)
            return
        }
        try {
            plugin.beforeUnload()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to run beforeUnload $absolutePath: ${Log.getStackTraceString(t)}")
        }
        removeRegistered(plugin.filename ?: absolutePath)
        synchronized(plugins) { plugins.remove(absolutePath) }
    }

    private fun removeRegistered(sourcePlugin: String) {
        APIHolder.apis.filter { api -> api.sourcePlugin == sourcePlugin }.forEach {
            removePluginMapping(it)
        }
        APIHolder.allProviders.withLock {
            APIHolder.allProviders.removeAll { provider -> provider.sourcePlugin == sourcePlugin }
        }
        extractorApis.withLock {
            extractorApis.removeAll { extractor -> extractor.sourcePlugin == sourcePlugin }
        }
    }
}
