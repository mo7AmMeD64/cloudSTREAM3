package com.kodastream.app.manga.extension

import android.content.Context
import com.kodastream.app.manga.isSupportedLib
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.store.AvailableExtension
import eu.kanade.tachiyomi.compat.MangaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class InstallFailureStep { Download, Validate, Trust, Install, Load }

sealed interface InstallStep {
    data object Pending : InstallStep
    data class Downloading(val percent: Int) : InstallStep
    data object Installing : InstallStep
    data object Installed : InstallStep
    data class Error(
        val message: String,
        val step: InstallFailureStep,
        val name: String,
        val pkgName: String,
        val versionName: String,
        val stackTrace: String,
    ) : InstallStep
}

/** Marks which part of the install rejected the file; the UI shows it in the error dialog. */
internal class InstallStepException(
    val step: InstallFailureStep,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** The two IO halves of an install, so the step machine can run against a fake downloader. */
internal interface ExtensionInstallBackend {
    fun download(extension: AvailableExtension, target: File): Flow<Int>
    fun install(extension: AvailableExtension, target: File)
}

/** Pending -> download percentages -> Installing -> Installed, or Error; the temp file always goes. */
internal fun installExtensionFlow(
    extension: AvailableExtension,
    target: File,
    backend: ExtensionInstallBackend,
): Flow<InstallStep> = flow {
    emit(InstallStep.Pending)
    target.delete()
    var phase = InstallFailureStep.Download
    try {
        var lastPercent = -1
        backend.download(extension, target).collect { value ->
            val percent = value.coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                emit(InstallStep.Downloading(percent))
            }
        }
        phase = InstallFailureStep.Install
        emit(InstallStep.Installing)
        backend.install(extension, target)
        emit(InstallStep.Installed)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emit(
            InstallStep.Error(
                message = rootMessage(e),
                step = (e as? InstallStepException)?.step ?: phase,
                name = extension.name,
                pkgName = extension.pkgName,
                versionName = extension.versionName,
                stackTrace = e.stackTraceToString(),
            ),
        )
    } finally {
        target.delete()
    }
}.flowOn(Dispatchers.IO)

/** Downloads, validates and installs one extension as a private `.ext` file. */
class ExtensionInstaller(private val context: Context) {
    private val appContext = context.applicationContext
    private val trustStore = MangaTrustStore(appContext)

    fun install(extension: AvailableExtension): Flow<InstallStep> {
        val temp = File(appContext.cacheDir, "manga_install").apply { mkdirs() }
            .let { File(it, "${extension.pkgName}.apk.part") }
        return installExtensionFlow(extension, temp, Backend())
    }

    private inner class Backend : ExtensionInstallBackend {
        override fun download(extension: AvailableExtension, target: File): Flow<Int> = flow {
            val engine = MangaEngine.get(appContext)
            val network = engine.network
                ?: throw IOException(engine.error ?: "The manga engine is not available")
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
                    target.outputStream().use { output ->
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
                                    emit(percent)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun install(extension: AvailableExtension, target: File) {
            val packageInfo = MangaPackageUtils.archivePackageInfo(appContext, target)
                ?: throw InstallStepException(
                    InstallFailureStep.Validate,
                    "The downloaded file is not a valid APK",
                )
            if (packageInfo.packageName != extension.pkgName) {
                throw InstallStepException(
                    InstallFailureStep.Validate,
                    "The APK package does not match the store listing",
                )
            }
            if (!MangaPackageUtils.isExtension(packageInfo)) {
                throw InstallStepException(
                    InstallFailureStep.Validate,
                    "The APK is not a Mihon extension",
                )
            }
            val lib = MangaPackageUtils.libVersion(packageInfo)
            if (!isSupportedLib(lib)) {
                throw InstallStepException(
                    InstallFailureStep.Validate,
                    "Unsupported extension library version: $lib",
                )
            }
            val classNames = packageInfo.applicationInfo?.metaData
                ?.getString(MangaPackageUtils.METADATA_SOURCE_CLASS)
            if (classNames.isNullOrBlank()) {
                throw InstallStepException(
                    InstallFailureStep.Validate,
                    "The APK has no source class",
                )
            }
            val signatures = MangaPackageUtils.signatureSha256(packageInfo)
            if (signatures.isEmpty()) {
                throw InstallStepException(
                    InstallFailureStep.Trust,
                    "The APK is not signed",
                )
            }
            val trusted = isExtensionTrusted(
                pkgName = packageInfo.packageName,
                versionCode = MangaPackageUtils.versionCode(packageInfo),
                signatures = signatures,
                storeSigningKeys = listOf(extension.storeSigningKey),
                manualTrustKeys = trustStore.trustedKeys(),
            )
            if (!trusted) {
                throw InstallStepException(
                    InstallFailureStep.Trust,
                    "The APK is not signed with the store's signing key",
                )
            }
            if (extension.versionCode > 0 && MangaPackageUtils.versionCode(packageInfo) != extension.versionCode) {
                throw InstallStepException(
                    InstallFailureStep.Validate,
                    "The downloaded version does not match the store listing",
                )
            }

            installPrivate(packageInfo, target)
        }
    }

    private fun installPrivate(packageInfo: android.content.pm.PackageInfo, source: File) {
        val target = File(
            MangaPackageUtils.extensionDir(appContext),
            "${packageInfo.packageName}.${MangaPackageUtils.PRIVATE_EXTENSION_EXTENSION}",
        )
        val current = if (target.isFile) MangaPackageUtils.archivePackageInfo(appContext, target) else null
        if (current != null) {
            if (MangaPackageUtils.versionCode(packageInfo) < MangaPackageUtils.versionCode(current)) {
                throw InstallStepException(
                    InstallFailureStep.Install,
                    "The installed version is newer, and downgrading isn't allowed",
                )
            }
            val currentSignatures = MangaPackageUtils.signatureSha256(current)
            if (
                currentSignatures.isEmpty() ||
                !MangaPackageUtils.signatureSha256(packageInfo).containsAll(currentSignatures)
            ) {
                throw InstallStepException(
                    InstallFailureStep.Install,
                    "It isn't signed with the same key as the installed extension",
                )
            }
        }

        target.parentFile?.mkdirs()
        val staged = File(target.parentFile, "${target.name}.part")
        try {
            source.copyTo(staged, overwrite = true)
            staged.setReadOnly()
            if (!staged.renameTo(target)) {
                throw InstallStepException(
                    InstallFailureStep.Install,
                    "Unable to replace ${target.name}",
                )
            }
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
