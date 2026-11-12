// Ported from Mihon's data/download/DownloadCache.kt (Apache-2.0), minus the
// persisted disk index: KodaStream lists the manga folder once per screen.
package com.kodastream.app.manga.download

import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/** Tracks which chapters of a manga are downloaded, for the details rows. */
object MangaDownloadCache {
    private val index = ConcurrentHashMap<String, Set<String>>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emits whenever a chapter is added or removed. */
    val changes = _changes.asSharedFlow()

    /** The downloaded chapter URLs, listing the folder only on the first query. */
    suspend fun downloadedUrls(
        provider: MangaDownloadProvider,
        manga: MangaEntry,
        chapters: List<MangaChapter>,
    ): Set<String> {
        index[manga.key()]?.let { return it }
        val found = provider.findDownloadedChapterDirs(manga, chapters).keys
        index[manga.key()] = found
        return found
    }

    fun add(manga: MangaEntry, chapterUrl: String) {
        index[manga.key()] = (index[manga.key()].orEmpty()) + chapterUrl
        _changes.tryEmit(Unit)
    }

    fun remove(manga: MangaEntry, chapterUrls: Collection<String>) {
        if (chapterUrls.isEmpty()) return
        index[manga.key()] = index[manga.key()].orEmpty() - chapterUrls.toSet()
        _changes.tryEmit(Unit)
    }

    fun invalidate(manga: MangaEntry) {
        index.remove(manga.key())
        _changes.tryEmit(Unit)
    }

    fun invalidateAll() {
        index.clear()
        _changes.tryEmit(Unit)
    }
}
