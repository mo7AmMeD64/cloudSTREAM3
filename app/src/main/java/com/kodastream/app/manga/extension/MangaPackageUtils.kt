package com.kodastream.app.manga.extension

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.kodastream.app.manga.store.ContentWarning
import java.security.MessageDigest

/** Package-level checks shared by the installer and the loader (Mihon's rules). */
internal object MangaPackageUtils {
    const val EXTENSION_FEATURE = "tachiyomi.extension"
    const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    const val METADATA_NAME = "tachiyomix.name"
    const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    const val PRIVATE_EXTENSION_EXTENSION = "ext"

    val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

    @Suppress("DEPRECATION")
    val PACKAGE_FLAGS: Int = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun extensionDir(context: Context) = java.io.File(context.filesDir, "exts")

    fun archivePackageInfo(context: Context, file: java.io.File): PackageInfo? {
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
            ?.also { it.applicationInfo?.fixBasePaths(file.absolutePath) }
    }

    fun isExtension(packageInfo: PackageInfo): Boolean =
        packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    fun versionCode(packageInfo: PackageInfo): Long =
        PackageInfoCompat.getLongVersionCode(packageInfo)

    fun libVersion(packageInfo: PackageInfo): Double? {
        val metadata = packageInfo.applicationInfo?.metaData ?: return null
        if (!metadata.containsKey(METADATA_EXTENSION_LIB)) return null
        return runCatching { metadata.getFloat(METADATA_EXTENSION_LIB).toDouble() }.getOrNull()
    }

    fun contentWarning(packageInfo: PackageInfo): ContentWarning {
        val metadata = packageInfo.applicationInfo?.metaData ?: return ContentWarning.SAFE
        return when {
            metadata.containsKey(METADATA_CONTENT_WARNING) -> when (metadata.getInt(METADATA_CONTENT_WARNING)) {
                1 -> ContentWarning.MIXED
                2 -> ContentWarning.NSFW
                else -> ContentWarning.SAFE
            }
            metadata.getInt(METADATA_NSFW) == 1 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }
    }

    fun signatureSha256(packageInfo: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        } ?: return emptyList()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    fun displayName(context: Context, packageInfo: PackageInfo): String {
        val metadata = packageInfo.applicationInfo?.metaData
        return metadata?.getString(METADATA_NAME)
            ?: packageInfo.applicationInfo?.let {
                context.packageManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ")
            }
            ?: packageInfo.packageName
    }

    fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) sourceDir = apkPath
        if (publicSourceDir == null) publicSourceDir = apkPath
    }
}
