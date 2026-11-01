package com.kodastream.app.manga

import android.content.Context
import androidx.core.content.edit

/**
 * Remembered choices for the manga section, kept apart from the video preferences.
 * Languages default to the app language and English, like Mihon's extension filters.
 */
object MangaPrefs {
    private const val PREFS = "manga_prefs"
    private const val KEY_NSFW = "show_nsfw"
    private const val KEY_ALL_LANGUAGES = "all_languages"
    private const val KEY_LANGUAGES = "languages"
    private const val KEY_SECTION = "manga_section"

    const val ALL_LANGUAGES = "all"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showNsfw(context: Context): Boolean = prefs(context).getBoolean(KEY_NSFW, false)

    fun setShowNsfw(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_NSFW, value) }
    }

    fun allLanguages(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALL_LANGUAGES, false)

    /** The selected language codes; the default is the app language plus English. */
    fun languages(context: Context): Set<String> {
        val saved = prefs(context).getStringSet(KEY_LANGUAGES, null)
        if (saved != null) return saved
        val appLanguage = context.resources.configuration.locales[0].language
        return setOf(appLanguage, "en").filter { it.isNotBlank() }.toSet()
    }

    fun setAllLanguages(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ALL_LANGUAGES, value) }
    }

    fun setLanguages(context: Context, languages: Set<String>) {
        prefs(context).edit { putStringSet(KEY_LANGUAGES, languages) }
    }

    /** Master switch for the manga mode; on until the owner turns it off. */
    fun mangaSectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SECTION, true)

    fun setMangaSectionEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SECTION, value) }
    }
}
