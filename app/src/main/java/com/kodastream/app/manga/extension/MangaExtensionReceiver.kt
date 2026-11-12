package com.kodastream.app.manga.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reloads the source list when a shared extension is installed, updated or removed. */
class MangaExtensionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED,
            -> Unit
            else -> return
        }
        val pkgName = intent.data?.schemeSpecificPart ?: return
        MangaSourceManager.get(context).onPackageChanged(pkgName)
    }
}
