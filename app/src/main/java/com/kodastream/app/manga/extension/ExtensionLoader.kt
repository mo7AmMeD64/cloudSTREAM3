package com.kodastream.app.manga.extension

import android.content.Context
import android.content.pm.PackageInfo
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
 * Port of Mihon's extension loader. Two kinds of extensions are supported:
 * shared ones installed by the system (usable by every Tachiyomi fork) and
 * private `.ext` files inside the app's data directory. When both exist under
 * one package name the higher version code wins, shared on a tie.
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
        val privatePackages = MangaPackageUtils.extensionDir(appContext)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull { file ->
                // Android 14+ requires private extension files to be read-only.
                if (file.canWrite()) file.setReadOnly()
                MangaPackageUtils.archivePackageInfo(appContext, file)
                    ?.takeIf { MangaPackageUtils.isExtension(it) }
                    ?.let { ExtensionPackage(it, isShared = false) }
            }
            ?.toList()
            .orEmpty()
        val privateByName = privatePackages.groupBy { it.packageInfo.packageName }

        val sharedPackages = MangaPackageUtils.installedPackages(appContext)
            .asSequence()
            .filter { MangaPackageUtils.isExtension(it) }
            .map { ExtensionPackage(it, isShared = true) }

        val packages = (sharedPackages + privatePackages)
            .distinctBy { it.packageInfo.packageName }
            .mapNotNull { packageInfo ->
                selectPackage(packageInfo, privateByName[packageInfo.packageInfo.packageName]?.singleOrNull())
            }
            .toList()
        if (packages.isEmpty()) return@withContext emptyList()

        coroutineScope {
            packages.map { packageInfo ->
                async {
                    semaphore.withPermit {
                        loadPackage(packageInfo, stores, alreadyLoaded)
                    }
                }
            }.awaitAll()
        }
    }

    fun cached(): Map<String, MangaExtension.Loaded> = cache.toMap()

    /** Loads one package after install, so the row appears without a full reload. */
    suspend fun loadPackage(pkgName: String, stores: List<ExtensionStore>): MangaExtension.Installed? =
        resolvePackage(pkgName)?.let { loadPackage(it, stores, cache) }

    fun clearCache() {
        cache.clear()
    }

    private suspend fun loadPackage(
        extensionPackage: ExtensionPackage,
        stores: List<ExtensionStore>,
        alreadyLoaded: Map<String, MangaExtension.Loaded>,
    ): MangaExtension.Installed {
        val packageInfo = extensionPackage.packageInfo
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
            isShared = extensionPackage.isShared,
            reason = reason,
            contentWarning = contentWarning,
        )

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
            if (
                loaded.versionCode == versionCode &&
                loaded.signatures == signatures &&
                loaded.isShared == extensionPackage.isShared
            ) {
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
            isShared = extensionPackage.isShared,
            sources = sources,
            factoryClassName = metadata.getString(MangaPackageUtils.METADATA_SOURCE_FACTORY),
        )
        cache[pkgName] = loaded
        return loaded
    }

    private fun resolvePackage(pkgName: String): ExtensionPackage? {
        val privateFile = File(
            MangaPackageUtils.extensionDir(appContext),
            "$pkgName.${MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION}",
        )
        val privatePackage = if (privateFile.isFile) {
            MangaPackageUtils.archivePackageInfo(appContext, privateFile)
                ?.takeIf { MangaPackageUtils.isExtension(it) }
                ?.let { ExtensionPackage(it, isShared = false) }
        } else {
            null
        }
        val sharedPackage = MangaPackageUtils.installedPackage(appContext, pkgName)
            ?.takeIf { MangaPackageUtils.isExtension(it) }
            ?.let { ExtensionPackage(it, isShared = true) }
        return selectPackage(sharedPackage, privatePackage)
    }

    private fun selectPackage(shared: ExtensionPackage?, private: ExtensionPackage?): ExtensionPackage? =
        when {
            private == null && shared != null -> shared
            shared == null && private != null -> private
            shared == null && private == null -> null
            else -> if (
                preferShared(
                    sharedVersionCode = MangaPackageUtils.versionCode(shared!!.packageInfo),
                    privateVersionCode = MangaPackageUtils.versionCode(private!!.packageInfo),
                )
            ) {
                shared
            } else {
                private
            }
        }

    private fun notLoaded(
        name: String,
        pkgName: String,
        versionName: String,
        versionCode: Long,
        libVersion: Double?,
        signatures: List<String>,
        store: ExtensionStore?,
        isShared: Boolean,
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
        isShared = isShared,
        reason = reason,
    ).also { cache.remove(pkgName) }

    private data class ExtensionPackage(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/** Mihon's rule when both variants exist: higher version code wins, shared on a tie. */
internal fun preferShared(sharedVersionCode: Long, privateVersionCode: Long): Boolean =
    sharedVersionCode >= privateVersionCode
