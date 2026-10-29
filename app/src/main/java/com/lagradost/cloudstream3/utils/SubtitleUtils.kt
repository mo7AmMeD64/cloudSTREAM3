package com.lagradost.cloudstream3.utils

/**
 * The subtitle-file matching helpers the player uses. The download-folder
 * cleanup from the original belongs to the download subsystem and is not
 * ported.
 */
object SubtitleUtils {
    // Only these files are allowed, so no videos as subtitles
    private val allowedExtensions = listOf(
        ".vtt", ".srt", ".txt", ".ass",
        ".ttml", ".sbv", ".dfxp"
    )

    fun isMatchingSubtitle(
        name: String,
        display: String,
        cleanDisplay: String
    ): Boolean {
        val hasValidExtension = allowedExtensions.any { name.endsWith(it, ignoreCase = true) }
        val isNotDisplayName = !name.equals(display, ignoreCase = true)
        val startsWithCleanDisplay =
            cleanDisplayName(name).startsWith(cleanDisplay, ignoreCase = true)
        return hasValidExtension && isNotDisplayName && startsWithCleanDisplay
    }

    fun cleanDisplayName(name: String): String {
        return name.substringBeforeLast('.').trim()
    }
}
