// Ported from Mihon's StorageManager (Apache-2.0): one base folder, with the
// chapter downloads kept in a "downloads" child, defaulting to the app folder.
package com.kodastream.app.manga.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.hippo.unifile.UniFile
import com.kodastream.app.manga.MangaPrefs
import java.io.File

/** Owns where chapter downloads land: the app folder or a user-picked SAF tree. */
object MangaDownloadStorage {
    private const val DOWNLOADS_PATH = "downloads"

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedDirectory: UniFile? = null

    /** The folder chapter folders live in, creating it when possible. */
    fun downloadsDirectory(context: Context): UniFile? {
        val tree = MangaPrefs.downloadTreeUri(context)
        val key = tree ?: "default"
        cachedKey?.let { if (it == key) return cachedDirectory }

        val directory = if (tree != null) {
            UniFile.fromUri(context, Uri.parse(tree))?.createDirectory(DOWNLOADS_PATH)
        } else {
            val dir = File(MangaDiskUtil.defaultBaseDirectory(context), DOWNLOADS_PATH)
                .apply { mkdirs() }
            UniFile.fromFile(dir)
        }
        MangaDiskUtil.createNoMediaFile(directory, context)
        cachedKey = key
        cachedDirectory = directory
        return directory
    }

    /** Takes write access to a picked tree; false when it is not writable. */
    fun adoptTree(context: Context, uri: Uri): Boolean {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Without the persisted grant the folder stops working after a restart.
            return false
        }
        if (UniFile.fromUri(context, uri)?.canWrite() != true) return false
        MangaPrefs.setDownloadTreeUri(context, uri.toString())
        MangaDownloadCache.invalidateAll()
        invalidate()
        return true
    }

    fun resetToDefault(context: Context) {
        MangaPrefs.setDownloadTreeUri(context, null)
        MangaDownloadCache.invalidateAll()
        invalidate()
    }

    /** What the settings row shows. */
    fun readableLocation(context: Context): String {
        val tree = MangaPrefs.downloadTreeUri(context)
        return if (tree != null) {
            val uri = Uri.parse(tree)
            val name = runCatching { UniFile.fromUri(context, uri)?.name }.getOrNull()
                ?: DocumentsContract.getTreeDocumentId(uri)
            "📁 $name/$DOWNLOADS_PATH"
        } else {
            File(MangaDiskUtil.defaultBaseDirectory(context), DOWNLOADS_PATH).absolutePath
        }
    }

    fun invalidate() {
        cachedKey = null
        cachedDirectory = null
    }
}
