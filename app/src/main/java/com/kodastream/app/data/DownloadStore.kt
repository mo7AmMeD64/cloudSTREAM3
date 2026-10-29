package com.kodastream.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kodastream.app.R
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

/**
 * The download list: one JSON record per file, same persistence and same
 * in-memory-plus-background-write pattern as History. Each record carries
 * its own full file URI, so entries keep playing and keep being deletable
 * even after the download location changes under them.
 */
object DownloadStore {
    const val STATE_QUEUED = "queued"
    const val STATE_DOWNLOADING = "downloading"
    const val STATE_PAUSED = "paused"
    const val STATE_FAILED = "failed"
    const val STATE_DONE = "done"

    data class Download(
        /** idOf(): provider|details url|season|episode - matches the watched-store granularity. */
        val id: String,
        val apiName: String,
        val url: String,
        val title: String,
        val posterUrl: String? = null,
        val posterHeaders: Map<String, String>? = null,
        /** movie | series | anime */
        val type: String = "movie",
        val season: Int? = null,
        val episode: Int? = null,
        val episodeTitle: String? = null,
        val year: Int? = null,
        val linkUrl: String,
        val linkName: String = "",
        val quality: Int = -1,
        val headers: Map<String, String> = emptyMap(),
        /** direct | hls (dash is rejected before enqueue). */
        val streamType: String = "direct",
        /** The provider data string, so the episode can also be streamed. */
        val data: String? = null,
        val fileName: String,
        /** The title's own folder this file lives in - stable across downloads and restarts. */
        val folderUri: String? = null,
        val fileUri: String? = null,
        /** Internal-storage thumbnail; the user's folder holds only video files. */
        val posterUri: String? = null,
        /** The provider's subtitle track to save beside the video, when there is one. */
        val subLang: String? = null,
        val subUrl: String? = null,
        val subtitleUri: String? = null,
        val state: String = STATE_QUEUED,
        val receivedBytes: Long = 0,
        val totalBytes: Long = 0,
        /** Where an interrupted HLS download continues from. */
        val hlsSegment: Int = 0,
        val hlsTotal: Int = 0,
        val error: String? = null,
        val createdAt: Long = 0,
    ) {
        val isHls: Boolean get() = streamType == "hls"
        /** 0f..1f while downloading, or -1 when the size is still unknown. */
        val progressFraction: Float
            get() = when {
                state == STATE_DONE -> 1f
                isHls -> if (hlsTotal > 0) hlsSegment.toFloat() / hlsTotal else -1f
                totalBytes > 0 -> receivedBytes.toFloat() / totalBytes
                else -> -1f
            }
    }

    private const val STORE_KEY = "downloads"
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<Download>?>(null)
    val entries: StateFlow<List<Download>?> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    fun current(context: Context): List<Download> {
        if (!loaded) {
            loaded = true
            _entries.value = context.getKey<List<Download>>(STORE_KEY).orEmpty()
        }
        return _entries.value.orEmpty()
    }

    fun idOf(apiName: String, url: String, season: Int?, episode: Int?): String =
        "$apiName|$url|${season ?: -1}|${episode ?: -1}"

    fun find(context: Context, id: String): Download? = current(context).firstOrNull { it.id == id }

    /** False when this id is already in the list; nothing is overwritten. */
    fun enqueue(context: Context, download: Download): Boolean {
        if (current(context).any { it.id == download.id }) return false
        publish(context, (listOf(download) + current(context)).take(MAX_ENTRIES))
        return true
    }

    fun update(context: Context, id: String, transform: (Download) -> Download) {
        publish(context, current(context).map { if (it.id == id) transform(it) else it })
    }

    fun remove(context: Context, id: String) {
        publish(context, current(context).filter { it.id != id })
    }

    fun clear(context: Context) {
        publish(context, emptyList())
    }

    private fun publish(context: Context, next: List<Download>) {
        _entries.value = next
        scope.launch { context.setKey(STORE_KEY, next) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Where downloads land and how to write there. Two modes from one setting:
 * the app's default folder inside app-specific external storage, or any
 * folder the user picked through SAF (tree URI + persistable permission).
 * No MANAGE_EXTERNAL_STORAGE, ever.
 */
object DownloadLocations {

    /** The app's own folder in app-specific external storage - no permission needed. */
    fun defaultDir(context: Context): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            context.getString(R.string.app_name),
        ).apply { mkdirs() }

    /** The picked tree, if it still exists and is still writable; clears the pref when it is not. */
    fun validTree(context: Context): DocumentFile? {
        val uri = AppPrefs.downloadTreeUri(context) ?: return null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(uri))
        if (tree == null || !tree.canWrite()) {
            AppPrefs.setDownloadTreeUri(context, null)
            return null
        }
        return tree
    }

    /** Takes write access to a user-picked folder and stores it; false when it is not writable. */
    fun adoptTree(context: Context, uri: Uri): Boolean {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        if (DocumentFile.fromTreeUri(context, uri)?.canWrite() != true) return false
        AppPrefs.setDownloadTreeUri(context, uri.toString())
        return true
    }

    /** What the settings row shows: the picked folder's name, or the default path. */
    fun readableLocation(context: Context): String {
        val tree = AppPrefs.downloadTreeUri(context)?.let { Uri.parse(it) }
        return if (tree != null) {
            val name = DocumentFile.fromTreeUri(context, tree)?.name
                ?: DocumentsContract.getTreeDocumentId(tree)
            "📁 $name"
        } else {
            defaultDir(context).absolutePath
        }
    }

    /**
     * Creates (or re-uses for resume) the target file and returns its URI.
     * A fresh start deletes any earlier partial file of the same name first.
     */
    /**
     * A real directory handle for a stored folder URI. fromSingleUri yields a
     * SingleDocumentFile that cannot create files or list children - it throws
     * UnsupportedOperationException - so the folder is looked up as a child
     * of the current tree instead, which gives a fully functional one.
     */
    private fun folderDoc(context: Context, folderUriString: String): DocumentFile? {
        val tree = validTree(context) ?: return null
        val target = Uri.parse(folderUriString)
        return tree.listFiles().firstOrNull { it.uri == target }
    }

    /** Creates [fileName] inside the stored folder and returns its URI. Null on failure. */
    fun createFileInFolder(
        context: Context,
        folderUriString: String,
        fileName: String,
        mime: String,
    ): String? {
        if (folderUriString.startsWith("content://")) {
            val folder = folderDoc(context, folderUriString) ?: return null
            val doc = folder.createFile(mime, fileName.substringBeforeLast('.')) ?: return null
            return doc.uri.toString()
        }
        val dir = File(Uri.parse(folderUriString).path!!)
        if (!dir.exists() && !dir.mkdirs()) return null
        return Uri.fromFile(File(dir, fileName)).toString()
    }

    fun ensureTarget(context: Context, download: DownloadStore.Download, fresh: Boolean): String? {
        // Resuming: the record already points at a file that still exists.
        if (!fresh) {
            download.fileUri?.let { uri ->
                if (exists(context, uri)) return uri
            }
        }

        val folderUri = download.folderUri ?: resolveFolder(context, download) ?: return null
        val mime = if (download.isHls) "video/mp2t" else "video/mp4"
        if (folderUri.startsWith("content://")) {
            val folder = folderDoc(context, folderUri) ?: return null
            if (fresh) folder.findFile(download.fileName)?.delete()
            val created = folder.createFile(mime, download.fileName.substringBeforeLast('.'))
            return created?.uri?.toString()
        }
        val folder = File(Uri.parse(folderUri).path!!)
        folder.mkdirs()
        if (fresh) File(folder, download.fileName).delete()
        return Uri.fromFile(File(folder, download.fileName)).toString()
    }

    // ---- one folder per title ----

    private fun sanitizeFilePart(name: String): String =
        name.replace(Regex("[\\/:*?\"<>|]"), " ")
            .trim()
            .trimEnd('.')
            .trim()
            .take(100)
            .ifBlank { "Video" }

    /** The folder name of a title: episodes share the series title, movies carry their year. */
    fun titleFolderName(download: DownloadStore.Download): String {
        val clean = sanitizeFilePart(download.title)
        return if (download.season == null && download.episode == null && download.year != null) {
            "$clean (${download.year})"
        } else {
            clean
        }
    }

    /**
     * The folder this title's files live in, stable across downloads and
     * restarts: the record's own folder first, then a sibling episode's,
     * then a fresh folder whose name no other title - and nothing the app
     * did not create - is already using. The URI is stored on the record,
     * so later episodes never recompute it, wherever the setting points.
     */
    fun resolveFolder(context: Context, download: DownloadStore.Download): String? {
        val records = DownloadStore.current(context)
        val titleId = "${download.apiName}|${download.url}"

        // This title already owns a folder - reuse it wherever it lives.
        val owned = download.folderUri
            ?: records.firstOrNull { "${it.apiName}|${it.url}" == titleId && it.folderUri != null }?.folderUri
        if (owned != null && folderExists(context, owned)) return owned

        val base = titleFolderName(download)
        val tree = validTree(context)

        var candidate = base
        var step = 0
        while (step < 12) {
            if (findChildFolder(context, tree, candidate) == null) {
                return createChildFolder(context, tree, candidate)
            }
            // A folder of this name exists but this title does not own it
            // (owned folders were reused above): disambiguate - year, then
            // source name, then a counter.
            candidate = when (step) {
                0 ->
                    if (download.year != null && !base.endsWith(" (${download.year})")) {
                        "$base (${download.year})"
                    } else {
                        "$base (${download.apiName})"
                    }

                1 -> "$base (${download.apiName})"
                else -> "$base (${step - 1})"
            }
            step++
        }
        return createChildFolder(context, tree, "$base ${System.currentTimeMillis()}")
    }

    private fun findChildFolder(context: Context, tree: DocumentFile?, name: String): String? {
        if (tree != null) return tree.findFile(name)?.uri?.toString()
        val folder = File(defaultDir(context), name)
        return if (folder.exists()) Uri.fromFile(folder).toString() else null
    }

    private fun createChildFolder(context: Context, tree: DocumentFile?, name: String): String? {
        if (tree != null) return tree.createDirectory(name)?.uri?.toString()
        val folder = File(defaultDir(context), name)
        if (!folder.exists() && !folder.mkdirs()) return null
        return Uri.fromFile(folder).toString()
    }

    fun folderExists(context: Context, uriString: String): Boolean = when {
        uriString.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.exists() == true
        uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).exists()
        else -> false
    }

    /** Deletes the folder when nothing is left in it - never throws. */
    fun deleteFolderIfEmpty(context: Context, uriString: String?): Boolean {
        if (uriString == null || !folderExists(context, uriString)) return false
        return try {
            val isEmpty = when {
                uriString.startsWith("content://") ->
                    folderDoc(context, uriString)?.listFiles()?.isEmpty() == true
                uriString.startsWith("file://") ->
                    File(Uri.parse(uriString).path!!).listFiles().isNullOrEmpty()
                else -> return false
            }
            isEmpty && delete(context, uriString)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Deletes a whole title folder, but only when every file inside belongs
     * to our own records - anything the app did not create stays untouched.
     */
    fun deleteTitleFolder(context: Context, folderUri: String?, ourFiles: Set<String>): Boolean {
        if (folderUri == null || !folderExists(context, folderUri)) return false
        return try {
            val childUris: List<String> = when {
                folderUri.startsWith("content://") ->
                    folderDoc(context, folderUri)?.listFiles()
                        ?.mapNotNull { it.uri?.toString() }
                        ?: return delete(context, folderUri)

                folderUri.startsWith("file://") ->
                    File(Uri.parse(folderUri).path!!).listFiles()
                        ?.map { Uri.fromFile(it).toString() }
                        ?: return delete(context, folderUri)

                else -> return delete(context, folderUri)
            }
            // Foreign files present: remove only ours and keep the folder.
            if (childUris.any { it !in ourFiles }) {
                childUris.filter { it in ourFiles }.forEach { delete(context, it) }
                return false
            }
            delete(context, folderUri)
        } catch (_: Throwable) {
            false
        }
    }

    fun exists(context: Context, uriString: String): Boolean = try {
        when {
            uriString.startsWith("content://") ->
                DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.exists() == true
            uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).exists()
            else -> false
        }
    } catch (_: Throwable) {
        false
    }

    fun fileLength(context: Context, uriString: String): Long = when {
        uriString.startsWith("content://") ->
            DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.length() ?: 0
        uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).length()
        else -> 0
    }

    fun openOutput(context: Context, uriString: String, append: Boolean): OutputStream = when {
        uriString.startsWith("content://") ->
            context.contentResolver.openOutputStream(Uri.parse(uriString), if (append) "wa" else "wt")
                ?: throw java.io.IOException("Cannot open $uriString")
        uriString.startsWith("file://") ->
            File(Uri.parse(uriString).path!!).outputStream(append)
        else -> throw java.io.IOException("Not a file URI: $uriString")
    }

    private fun File.outputStream(append: Boolean): OutputStream =
        if (append) java.io.FileOutputStream(this, true) else java.io.FileOutputStream(this)

    /** Never throws: the UI calls this on the main thread. */
    fun delete(context: Context, uriString: String?): Boolean {
        if (uriString == null) return false
        return try {
            when {
                uriString.startsWith("content://") ->
                    DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.delete() == true
                uriString.startsWith("file://") -> File(Uri.parse(uriString).path!!).delete()
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** Best-effort free space at the download location. */
    fun freeBytes(context: Context): Long =
        validTree(context)?.let { defaultDir(context).usableSpace } ?: defaultDir(context).usableSpace
}
