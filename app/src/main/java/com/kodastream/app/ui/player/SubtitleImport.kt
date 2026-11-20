package com.kodastream.app.ui.player

/** File-name rules for a subtitle picked from storage. */
object SubtitleImport {
    private val supported = setOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "xml")

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it in supported }.orEmpty()

    /** Returns a cache-safe name, or null when the picked file is not a subtitle. */
    fun fileName(displayName: String, stamp: Long): String? {
        val ext = extension(displayName).ifEmpty { return null }
        val base = displayName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._ -]"), "")
            .ifBlank { "subtitle" }
        return "${stamp}_$base.$ext"
    }
}
