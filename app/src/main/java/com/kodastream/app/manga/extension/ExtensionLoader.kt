package com.kodastream.app.manga.extension

import android.content.Context
import com.kodastream.app.manga.isSupportedLib
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.signatureMatches
import com.kodastream.app.manga.store.ContentWarning
import com.kodastream.app.manga.store.ExtensionStore
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mihon.data.dalvik.DelegateLastClassLoaderCompat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Private-extension loader, a port of Mihon's loader without the shared-package scan.
 * Each APK is isolated: a bad one becomes [MangaExtension.NotLoaded] and never blocks
 * the others.
 */
class ExtensionLoader(private val context: Context) {
    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, MangaExtension.Loaded>()
    private val semaphore = Semaphore(4)
    private val trustStore = MangaTrustStore(appContext)

    suspend fun loadAll(
        stores: List<ExtensionStore>,
        alreadyLoaded: Map<String, MangaExtension.Loaded> = cache,
    ): List<MangaExtension.Installed> = withContext(Dispatchers.IO) {
        val files = MangaPackageUtils.extensionDir(appContext)
            .listFiles()
            ?.filter { it.isFile && it.extension == MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION }
            .orEmpty()
        if (files.isEmpty()) return@withContext emptyList()

        coroutineScope {
            files.map { file ->
                async {
                    semaphore.withPermit {
                        loadFile(file, stores, alreadyLoaded)
                    }
                }
            }.awaitAll()
        }
    }

    fun cached(): Map<String, MangaExtension.Loaded> = cache.toMap()

    /** Loads one file, used right after install so the row appears without a full reload. */
    fun loadOne(file: File, stores: List<ExtensionStore>): MangaExtension.Installed =
        loadFile(file, stores, cache)

    fun clearCache() {
        cache.clear()
    }

    private fun loadFile(
        file: File,
        stores: List<ExtensionStore>,
        alreadyLoaded: Map<String, MangaExtension.Loaded>,
    ): MangaExtension.Installed {
        val packageInfo = try {
            MangaPackageUtils.archivePackageInfo(appContext, file)
        } catch (e: Throwable) {
            null
        } ?: return notLoaded(
            name = file.name.substringBefore("."),
            pkgName = file.name.substringBefore("."),
            versionName = "",
            versionCode = 0,
            libVersion = null,
            signatures = emptyList(),
            store = null,
            reason = MangaExtension.NotLoaded.Reason.Malformed,
        )

        val pkgName = packageInfo.packageName
        val versionCode = MangaPackageUtils.versionCode(packageInfo)
        val signatures = MangaPackageUtils.signatureSha256(packageInfo)
        val store = stores.firstOrNull { signatureMatches(signatures, it.signingKey) }
        val name = MangaPackageUtils.displayName(appContext, packageInfo)
        val contentWarning = try {
            MangaPackageUtils.contentWarning(packageInfo)
        } catch (_: Throwable) {
            ContentWarning.SAFE
        }

        fun failed(
            reason: MangaExtension.NotLoaded.Reason,
            libVersion: Double? = null,
        ) = notLoaded(
            name = name,
            pkgName = pkgName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = versionCode,
            libVersion = libVersion,
            signatures = signatures,
            store = store,
            reason = reason,
            contentWarning = contentWarning,
        )

        if (!MangaPackageUtils.isExtension(packageInfo)) return failed(MangaExtension.NotLoaded.Reason.Malformed)
        if (packageInfo.versionName.isNullOrEmpty()) return failed(MangaExtension.NotLoaded.Reason.Malformed)

        val libVersion = MangaPackageUtils.libVersion(packageInfo)
        if (!isSupportedLib(libVersion)) {
            return failed(MangaExtension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }
        if (signatures.isEmpty()) return failed(MangaExtension.NotLoaded.Reason.Unsigned, libVersion)
        if (!trustStore.isTrusted(packageInfo, signatures, stores)) {
            return failed(MangaExtension.NotLoaded.Reason.Untrusted(signatures.last()), libVersion)
        }

        alreadyLoaded[pkgName]?.let { loaded ->
            if (loaded.versionCode == versionCode && loaded.signatures == signatures) {
                return loaded.copy(store = store)
            }
        }

        val appInfo = packageInfo.applicationInfo ?: return failed(MangaExtension.NotLoaded.Reason.Malformed, libVersion)
        val metadata = appInfo.metaData ?: return failed(MangaExtension.NotLoaded.Reason.Malformed, libVersion)
        val classNames = metadata.getString(MangaPackageUtils.METADATA_SOURCE_CLASS)
        if (classNames.isNullOrBlank()) return failed(MangaExtension.NotLoaded.Reason.Malformed, libVersion)

        val classLoader = try {
            DelegateLastClassLoaderCompat(appInfo.sourceDir, null, appContext.classLoader)
        } catch (e: Throwable) {
            return failed(
                MangaExtension.NotLoaded.Reason.Failed(rootMessage(e), e.stackTraceToString()),
                libVersion,
            )
        }

        val sources = classNames.split(";").flatMap { rawName ->
            val className = rawName.trim().let {
                if (it.startsWith(".")) pkgName + it else it
            }
            try {
                when (val instance = Class.forName(className, false, classLoader)
                    .getDeclaredConstructor()
                    .newInstance()) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> throw IllegalStateException("Unknown source class type: ${instance.javaClass.name}")
                }
            } catch (e: Throwable) {
                return failed(
                    MangaExtension.NotLoaded.Reason.Failed(rootMessage(e), e.stackTraceToString()),
                    libVersion,
                )
            }
        }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val loaded = MangaExtension.Loaded(
            name = name,
            pkgName = pkgName,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            contentWarning = contentWarning,
            store = store,
            signatures = signatures,
            sources = sources,
            factoryClassName = metadata.getString(MangaPackageUtils.METADATA_SOURCE_FACTORY),
        )
        cache[pkgName] = loaded
        return loaded
    }

    private fun notLoaded(
        name: String,
        pkgName: String,
        versionName: String,
        versionCode: Long,
        libVersion: Double?,
        signatures: List<String>,
        store: ExtensionStore?,
        reason: MangaExtension.NotLoaded.Reason,
        contentWarning: ContentWarning = ContentWarning.SAFE,
    ) = MangaExtension.NotLoaded(
        name = name,
        pkgName = pkgName,
        versionName = versionName,
        versionCode = versionCode,
        libVersion = libVersion,
        lang = null,
        contentWarning = contentWarning,
        store = store,
        signatures = signatures,
        reason = reason,
    ).also { cache.remove(pkgName) }
}
