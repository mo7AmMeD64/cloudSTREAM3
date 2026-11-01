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
    ): Boolean {
        if (signatures.isEmpty()) return false
        val storeKeys = stores.map { it.signingKey }.filter { it.isNotBlank() }.toSet()
        if (signatures.any { it in storeKeys }) return true
        val keys = trustedKeys()
        return signatures.any { signature ->
            "${packageInfo.packageName}:${PackageInfoCompat.getLongVersionCode(packageInfo)}:$signature" in keys
        }
    }

    fun trust(packageInfo: PackageInfo, signature: String) {
        trust(packageInfo.packageName, PackageInfoCompat.getLongVersionCode(packageInfo), signature)
    }

    fun trust(pkgName: String, versionCode: Long, signature: String) {
        val key = "$pkgName:$versionCode:$signature"
        prefs.edit().putStringSet(KEY, trustedKeys() + key).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "trusted"
    }
}
