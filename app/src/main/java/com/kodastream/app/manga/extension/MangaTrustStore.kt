package com.kodastream.app.manga.extension

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import com.kodastream.app.manga.store.ExtensionStore

/** Manual trust list, keyed exactly as `pkg:versionCode:signature`. */
class MangaTrustStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("manga_trust", Context.MODE_PRIVATE)

    fun trustedKeys(): Set<String> =
        prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun isTrusted(
        packageInfo: PackageInfo,
        signatures: List<String>,
        stores: List<ExtensionStore>,
    ): Boolean = isExtensionTrusted(
        pkgName = packageInfo.packageName,
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        signatures = signatures,
        storeSigningKeys = stores.map { it.signingKey },
        manualTrustKeys = trustedKeys(),
    )

    fun trust(packageInfo: PackageInfo, signature: String) {
        trust(packageInfo.packageName, PackageInfoCompat.getLongVersionCode(packageInfo), signature)
    }

    fun trust(pkgName: String, versionCode: Long, signature: String) {
        val key = extensionTrustKey(pkgName, versionCode, signature)
        prefs.edit().putStringSet(KEY, trustedKeys() + key).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "trusted"
    }
}

internal fun extensionTrustKey(pkgName: String, versionCode: Long, signature: String): String =
    "$pkgName:$versionCode:$signature"

/** Store keys trust any version of their extensions; manual keys trust one exact build. */
internal fun isExtensionTrusted(
    pkgName: String,
    versionCode: Long,
    signatures: List<String>,
    storeSigningKeys: Collection<String>,
    manualTrustKeys: Collection<String>,
): Boolean {
    if (signatures.isEmpty()) return false
    val storeKeys = storeSigningKeys.filter { it.isNotBlank() }.toSet()
    if (signatures.any { it in storeKeys }) return true
    return signatures.any { extensionTrustKey(pkgName, versionCode, it) in manualTrustKeys }
}
