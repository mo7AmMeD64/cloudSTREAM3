package com.kodastream.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * How the player draws subtitles: size, colors, edge and font, the same set
 * of knobs CloudStream's player exposes. Stored as one object under the
 * engine's DataStore, so it survives restarts next to everything else.
 *
 * Colors are 0xAARRGGBB longs; the player passes them straight to media3's
 * CaptionStyleCompat as android ints.
 */
object SubtitleStyleStore {
    data class SubtitleStyle(
        /** Multiplier of media3's default caption size. */
        val fontSize: Float = 1.0f,
        val textColor: Long = 0xFFFFFFFF,
        /** 0 = transparent. */
        val bgColor: Long = 0x00000000,
        /** CaptionStyleCompat.EDGE_TYPE_*. */
        val edgeType: Int = 1,
        val edgeColor: Long = 0xFF000000,
        /** A file name inside filesDir/subtitle_fonts, or null for the default. */
        val fontFile: String? = null,
    )

    private const val STORE_KEY = "subtitle_style"
    private const val FONTS_DIR = "subtitle_fonts"

    private val _style = MutableStateFlow<SubtitleStyle?>(null)

    /** The live style; the player re-applies it the moment it changes. */
    val style: StateFlow<SubtitleStyle?> = _style.asStateFlow()

    fun current(context: Context): SubtitleStyle =
        _style.value
            ?: context.getKey<SubtitleStyle>(STORE_KEY)?.also { _style.value = it }
            ?: SubtitleStyle()

    fun update(context: Context, transform: (SubtitleStyle) -> SubtitleStyle): SubtitleStyle {
        val next = transform(current(context))
        _style.value = next
        context.setKey(STORE_KEY, next)
        return next
    }

    fun reset(context: Context) {
        val next = SubtitleStyle()
        _style.value = next
        context.setKey(STORE_KEY, next)
    }

    /** The imported font files, ready to become chips. */
    fun fonts(context: Context): List<String> =
        fontsDir(context).listFiles()
            ?.filter { it.extension.lowercase() in setOf("ttf", "otf") }
            ?.map { it.name }
            .orEmpty()

    /** Copies the picked font into the app's own folder and returns its file name. */
    fun importFont(context: Context, uri: Uri): String? = try {
        val dir = fontsDir(context)
        val resolver = context.contentResolver
        val display = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        val base = (display ?: "font").replace(Regex("[^A-Za-z0-9._ -]"), "").ifBlank { "font" }
        val ext = display?.substringAfterLast('.', "")?.lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
        val name = "${base.substringBeforeLast('.')}_${System.currentTimeMillis()}.$ext"
        val out = File(dir, name)
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return null
        name
    } catch (_: Exception) {
        null
    }

    fun deleteFont(context: Context, name: String) {
        File(fontsDir(context), name).delete()
        // If the deleted font was in use, fall back to the default.
        if (current(context).fontFile == name) update(context) { it.copy(fontFile = null) }
    }

    private fun fontsDir(context: Context): File =
        File(context.filesDir, FONTS_DIR).apply { mkdirs() }
}
