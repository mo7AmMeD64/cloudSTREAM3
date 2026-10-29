package com.kodastream.app.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.data.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-time move of pre-update downloads, which sat scattered directly in
 * the download location, into their title folders. Safe to re-run: records
 * that already carry a folder are skipped, and any file that fails to move
 * keeps its old URI and keeps playing exactly as before.
 */
object DownloadMigration {

    suspend fun run(context: Context) = withContext(Dispatchers.IO) {
        if (AppPrefs.downloadsFolderMigrated(context)) return@withContext

        val movable = DownloadStore.current(context).filter {
            it.fileUri != null && it.folderUri == null && it.state == DownloadStore.STATE_DONE
        }
        var moved = 0
        for (record in movable) {
            try {
                val fileUri = record.fileUri ?: continue
                val folder = DownloadLocations.resolveFolder(context, record) ?: continue
                val newUri = move(context, fileUri, folder, record.fileName) ?: continue
                DownloadStore.update(context, record.id) {
                    it.copy(folderUri = folder, fileUri = newUri)
                }
                moved++
            } catch (_: Throwable) {
                // Keep the old location: the download still works there.
            }
        }

        AppPrefs.setDownloadsFolderMigrated(context, true)
        if (moved > 0) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.downloads_migrated, moved),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** Moves the file into its folder: rename, then SAF move, then copy+delete. */
    private fun move(context: Context, sourceUri: String, folderUri: String, fileName: String): String? {
        // Plain files: a rename does it.
        if (sourceUri.startsWith("file://") && folderUri.startsWith("file://")) {
            val source = File(Uri.parse(sourceUri).path!!)
            val target = File(Uri.parse(folderUri).path!!, fileName)
            if (source.renameTo(target)) return Uri.fromFile(target).toString()
            // Cross-volume or busy: fall through to copy+delete.
            source.copyTo(target, overwrite = true)
            return if (source.delete()) Uri.fromFile(target).toString() else null
        }

        if (sourceUri.startsWith("content://") && folderUri.startsWith("content://")) {
            val resolver = context.contentResolver
            val source = Uri.parse(sourceUri)
            val targetFolder = Uri.parse(folderUri)
            // Pre-migration files sat directly in the location, so their
            // parent is the tree's root document.
            val sourceRoot = DocumentsContract.buildDocumentUriUsingTree(
                source,
                DocumentsContract.getTreeDocumentId(source),
            )
            try {
                val movedUri = DocumentsContract.moveDocument(resolver, source, sourceRoot, targetFolder)
                if (movedUri != null) return movedUri.toString()
            } catch (_: Throwable) {
                // Not movable in place: copy then delete below.
            }
        }

        // Copy + delete, the universal fallback.
        val sourceDoc = DocumentFile.fromSingleUri(context, Uri.parse(sourceUri)) ?: return null
        if (!sourceDoc.exists()) return null
        val target = DownloadLocations.ensureTarget(
            context,
            recordWith(sourceUri, folderUri, fileName),
            fresh = true,
        ) ?: return null
        context.contentResolver.openInputStream(sourceDoc.uri)?.use { input ->
            DownloadLocations.openOutput(context, target, append = false).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        return if (sourceDoc.delete()) target else null
    }

    /** A minimal record just for ensureTarget's shape. */
    private fun recordWith(fileUri: String, folderUri: String, fileName: String) =
        DownloadStore.Download(
            id = "migration",
            apiName = "migration",
            url = "migration",
            title = "migration",
            linkUrl = fileUri,
            fileName = fileName,
            fileUri = fileUri,
            folderUri = folderUri,
        )
}
