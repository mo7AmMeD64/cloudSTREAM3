// Ported from Mihon's core/common/util/storage/DiskUtil.kt (Apache-2.0).
package com.kodastream.app.manga.download

import android.content.Context
import android.os.StatFs
import com.hippo.unifile.UniFile
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** The file-name and storage rules Mihon's DownloadProvider and Downloader rely on. */
internal object MangaDiskUtil {

    const val NOMEDIA_FILE = ".nomedia"
    const val MAX_FILE_NAME_BYTES = 240

    fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun getAvailableStorageSpace(file: UniFile): Long = try {
        val stat = StatFs(file.uri.path)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (_: Exception) {
        -1L
    }

    fun createNoMediaFile(dir: UniFile?, context: Context?) {
        if (dir != null && dir.exists() && dir.findFile(NOMEDIA_FILE) == null) {
            dir.createFile(NOMEDIA_FILE)
        }
    }

    fun buildValidFilename(
        origName: String,
        maxBytes: Int = MAX_FILE_NAME_BYTES,
        disallowNonAscii: Boolean = false,
    ): String {
        val name = origName.trim('.', ' ')
        if (name.isEmpty()) return "(invalid)"
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (disallowNonAscii && c >= 0x80.toChar()) {
                c.toString().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                    .let(sb::append)
            } else if (isValidFatFilenameChar(c)) {
                sb.append(c)
            } else {
                sb.append('_')
            }
        }
        return truncateToLength(sb.toString(), maxBytes)
    }

    fun truncateToLength(s: String, maxBytes: Int): String {
        val charset = Charsets.UTF_8
        val bytes = s.toByteArray(charset)
        if (bytes.size <= maxBytes) return s
        val decoder = charset.newDecoder()
        val bb = ByteBuffer.wrap(bytes, 0, maxBytes)
        val cb = CharBuffer.allocate(maxBytes)
        decoder.onMalformedInput(CodingErrorAction.IGNORE)
        decoder.decode(bb, cb, true)
        decoder.flush(cb)
        return String(cb.array(), 0, cb.position())
    }

    private fun isValidFatFilenameChar(c: Char): Boolean {
        if (c in 0x00.toChar()..0x1f.toChar()) return false
        return when (c) {
            '"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7f.toChar() -> false
            else -> true
        }
    }

    /** The default root Mihon uses: the app's own external folder. */
    fun defaultBaseDirectory(context: Context): File =
        (context.getExternalFilesDir(null) ?: context.filesDir)

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp")

    fun isImageName(name: String?): Boolean =
        name != null && name.substringAfterLast('.', "").lowercase() in imageExtensions

    /** The image type from its header, the way Mihon names a downloaded page. */
    fun findImageExtension(input: InputStream): String? {
        val header = ByteArray(32)
        val count = input.read(header)
        if (count < 12) return null
        fun match(offset: Int, vararg bytes: Int): Boolean =
            bytes.indices.all { header[offset + it] == bytes[it].toByte() }
        return when {
            match(0, 0xFF, 0xD8, 0xFF) -> "jpg"
            match(0, 0x89, 0x50, 0x4E, 0x47) -> "png"
            match(0, 0x47, 0x49, 0x46, 0x38) -> "gif"
            match(0, 0x52, 0x49, 0x46, 0x46) && match(8, 0x57, 0x45, 0x42, 0x50) -> "webp"
            match(0, 0x42, 0x4D) -> "bmp"
            match(4, 0x66, 0x74, 0x79, 0x70) &&
                (match(8, 0x61, 0x76, 0x69, 0x66) || match(8, 0x61, 0x76, 0x69, 0x73)) -> "avif"
            else -> null
        }
    }
}
