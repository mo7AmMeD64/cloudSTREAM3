// Ported from Mihon's data/download/DownloadStore.kt (Apache-2.0), backed by the
// app's CloudStream DataStore instead of a preference file.
package com.kodastream.app.manga.download

import android.content.Context
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStore.setKeys

/** Everything needed to rebuild a queued download after the process dies. */
data class MangaDownloadRecord(
    val sourceId: Long = 0L,
    val mangaUrl: String = "",
    val mangaTitle: String = "",
    val sourceName: String = "",
    val thumbnailUrl: String? = null,
    val language: String = "",
    val chapterUrl: String = "",
    val chapterName: String = "",
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
    val sourceOrder: Int = 0,
    val memo: String = "",
    val queuedAt: Long = 0L,
)

fun MangaDownloadRecord.toManga(): MangaEntry = MangaEntry(
    sourceId = sourceId,
    url = mangaUrl,
    title = mangaTitle,
    thumbnailUrl = thumbnailUrl,
    sourceName = sourceName,
    language = language,
)

fun MangaDownloadRecord.toChapter(): MangaChapter = MangaChapter(
    url = chapterUrl,
    name = chapterName,
    number = chapterNumber,
    scanlator = scanlator,
    sourceOrder = sourceOrder,
    memo = memo,
)

fun MangaDownload.toRecord(): MangaDownloadRecord = MangaDownloadRecord(
    sourceId = manga.sourceId,
    mangaUrl = manga.url,
    mangaTitle = manga.title,
    sourceName = manga.sourceName,
    thumbnailUrl = manga.thumbnailUrl,
    language = manga.language,
    chapterUrl = chapter.url,
    chapterName = chapter.name,
    chapterNumber = chapter.number,
    scanlator = chapter.scanlator,
    sourceOrder = chapter.sourceOrder,
    memo = chapter.memo,
    queuedAt = queuedAt,
)

/** The active queue, persisted one record per chapter. */
object MangaDownloadStore {
    private const val FOLDER = "manga_downloads"

    fun restore(context: Context): List<MangaDownloadRecord> =
        context.getKeys(FOLDER)
            .mapNotNull { path -> context.getKey<MangaDownloadRecord>(path) }
            .sortedBy { it.queuedAt }

    fun addAll(context: Context, records: List<MangaDownloadRecord>) {
        if (records.isEmpty()) return
        context.setKeys(
            FOLDER,
            records.associate { record -> record.key() to record },
        )
    }

    fun remove(context: Context, records: List<MangaDownloadRecord>) {
        records.forEach { context.removeKey(FOLDER, it.key()) }
    }

    fun clear(context: Context) {
        context.removeKeys(FOLDER)
    }

    /** The source name stored with a title, for opening downloads without its extension. */
    fun sourceName(context: Context, sourceId: Long, mangaUrl: String): String? =
        restore(context).firstOrNull { it.sourceId == sourceId && it.mangaUrl == mangaUrl }?.sourceName

    private fun MangaDownloadRecord.key(): String = "$sourceId|$mangaUrl|$chapterUrl"
}
