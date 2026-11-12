package com.kodastream.app.manga

/**
 * The language filter shared by Browse and the Extensions screen. A multi-language
 * extension (`lang == "all"`) contains sources in every language, so it stays
 * visible; an extension with no known language is never hidden.
 */
data class MangaLanguageFilter(
    val all: Boolean,
    val languages: Set<String>,
) {
    fun matches(lang: String?): Boolean =
        all || languages.isEmpty() || lang.isNullOrBlank() || lang == "all" || lang in languages
}
