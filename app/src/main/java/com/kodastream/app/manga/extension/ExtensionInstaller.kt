package com.kodastream.app.manga.extension

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.kodastream.app.manga.store.AvailableExtension
import eu.kanade.tachiyomi.compat.MangaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed interface InstallStep {
    data object Pending : InstallStep
    data class Downloading(val percent: Int) : InstallStep
    data object Installing : InstallStep
    data object Installed : InstallStep
    data class Error(val message: String) : InstallStep
}

/** Downloads, validates and installs one extension as a private `.ext` file. */
class ExtensionInstaller(private val context: Context) {
    private val appContext = context.applicationContext
    private val trustStore = MangaTrustStore(appContext)

    fun install(extension: AvailableExtension): Flow<InstallStep> = flow {
        emit(InstallStep.Pending)
        val temp = File(appContext.cacheDir, "manga_install").apply { mkdirs() }
            .let { File(it, "${extension.pkgName}.apk.part") }
        temp.delete()
        try {
            val engine = MangaEngine.get(appContext)
            val network = engine.network ?: throw IOException(engine.error ?: "The manga engine is not available")
            val request = Request.Builder()
                .url(extension.apkUrl)
                .header("User-Agent", network.defaultUserAgentProvider())
                .build()
            network.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    emit(InstallStep.Downloading(percent))
                                }
                            }
                        }
                    }
                }
            }

            emit(InstallStep.Installing)
            val packageInfo = MangaPackageUtils.archivePackageInfo(appContext, temp)
                ?: throw IOException("The downloaded file is not a valid APK")
            if (packageInfo.packageName != extension.pkgName) {
                throw IOException("The APK package does not match the store listing")
            }
            if (!MangaPackageUtils.isExtension(packageInfo)) {
                throw IOException("The APK is not a Mihon extension")
            }
            val lib = MangaPackageUtils.libVersion(packageInfo)
            if (lib == null || lib !in MangaPackageUtils.SUPPORTED_LIB_VERSIONS) {
                throw IOException("Unsupported extension library version: $lib")
            }
            val classNames = packageInfo.applicationInfo?.metaData
                ?.getString(MangaPackageUtils.METADATA_SOURCE_CLASS)
            if (classNames.isNullOrBlank()) throw IOException("The APK has no source class")
            val signatures = MangaPackageUtils.signatureSha256(packageInfo)
            if (signatures.isEmpty()) throw IOException("The APK is not signed")
            val trusted = signatures.any { it == extension.storeSigningKey } ||
                trustStore.isTrusted(packageInfo, signatures, emptyList())
            if (!trusted) {
                throw IOException("The APK is not signed with the store's signing key")
            }
            if (extension.versionCode > 0 && MangaPackageUtils.versionCode(packageInfo) != extension.versionCode) {
                throw IOException("The downloaded version does not match the store listing")
            }

            installPrivate(packageInfo, temp)
            emit(InstallStep.Installed)
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: Exception) {
            temp.delete()
            emit(InstallStep.Error(e.message ?: "Install failed"))
        } finally {
            temp.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun installPrivate(packageInfo: android.content.pm.PackageInfo, source: File) {
        val target = File(
            MangaPackageUtils.extensionDir(appContext),
            "${packageInfo.packageName}.${MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION}",
        )
        val current = if (target.isFile) MangaPackageUtils.archivePackageInfo(appContext, target) else null
        if (current != null) {
            check(MangaPackageUtils.versionCode(packageInfo) >= MangaPackageUtils.versionCode(current)) {
                "The installed version is newer, and downgrading isn't allowed"
            }
            val currentSignatures = MangaPackageUtils.signatureSha256(current)
            check(
                currentSignatures.isNotEmpty() &&
                    MangaPackageUtils.signatureSha256(packageInfo).containsAll(currentSignatures),
            ) {
                "It isn't signed with the same key as the installed extension"
            }
        }

        target.parentFile?.mkdirs()
        val staged = File(target.parentFile, "${target.name}.part")
        try {
            source.copyTo(staged, overwrite = true)
            staged.setReadOnly()
            if (!staged.renameTo(target)) throw IOException("Unable to replace ${target.name}")
        } finally {
            staged.delete()
        }
    }

    fun uninstall(pkgName: String) {
        File(
            MangaPackageUtils.extensionDir(appContext),
            "$pkgName.${MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION}",
        ).delete()
    }

    fun trust(packageInfo: android.content.pm.PackageInfo, signature: String) {
        trustStore.trust(packageInfo, signature)
    }

    fun trust(pkgName: String, versionCode: Long, signature: String) {
        trustStore.trust(pkgName, versionCode, signature)
    }

    fun trustedKeys(): Set<String> = trustStore.trustedKeys()
}
