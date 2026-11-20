package com.kodastream.app.manga.translate

import android.content.Context
import java.io.File

/**
 * One small JSON file per page, under the app's private files. Keeping it out
 * of the page cache means clearing downloaded pages does not spend Gemini
 * quota again.
 */
object TranslationCache {
    private const val DIR = "translations"

    fun key(fileHash: String, variant: String, target: String, model: String): String =
        "${fileHash.take(32)}-" +
            "${Integer.toHexString(variant.hashCode())}-" +
            "$target-${Integer.toHexString(model.hashCode())}"

    fun read(context: Context, key: String): PageTranslation? {
        val file = File(directory(context), "$key.json")
        if (!file.isFile) return null
        return runCatching { TranslationJson.decode(file.readText()) }.getOrNull()
    }

    fun write(context: Context, key: String, translation: PageTranslation) {
        runCatching {
            val directory = directory(context)
            directory.mkdirs()
            File(directory, "$key.json").writeText(TranslationJson.encode(translation))
        }
    }

    fun clear(context: Context) {
        runCatching { directory(context).deleteRecursively() }
    }

    private fun directory(context: Context) = File(context.filesDir, DIR)
}
