package com.kodastream.app.manga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * Extensions are not required to set a URL or a title, and the compat models
 * keep those fields lateinit like Mihon. An unset value is treated as absent
 * so one incomplete result never kills a whole page.
 */
fun SManga.urlOrNull(): String? = try {
    url
} catch (_: UninitializedPropertyAccessException) {
    null
}

fun SManga.titleOrEmpty(): String = try {
    title
} catch (_: UninitializedPropertyAccessException) {
    ""
}

fun SChapter.urlOrNull(): String? = try {
    url
} catch (_: UninitializedPropertyAccessException) {
    null
}

fun SChapter.nameOrNull(): String? = try {
    name
} catch (_: UninitializedPropertyAccessException) {
    null
}

/**
 * Mihon's `UpdateMangaFromRemote` rules: the local source id and URL always
 * win, and a favourite keeps its title because title updates are off.
 */
fun MangaEntry.withRemoteDetails(remote: SManga, isFavorite: Boolean): MangaEntry {
    val remoteTitle = remote.titleOrEmpty()
    val remoteGenres = remote.getGenres()
    return copy(
        title = if (remoteTitle.isNotBlank() && !isFavorite) remoteTitle else title,
        thumbnailUrl = remote.thumbnail_url?.takeIf { it.isNotEmpty() } ?: thumbnailUrl,
        author = remote.author ?: author,
        artist = remote.artist ?: artist,
        description = remote.description ?: description,
        genres = remoteGenres ?: genres,
        status = remote.status,
        updateStrategy = runCatching { remote.update_strategy.name }
            .getOrDefault(updateStrategy),
        memo = remote.memo.toString(),
    )
}
