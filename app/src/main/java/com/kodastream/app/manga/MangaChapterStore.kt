package com.kodastream.app.manga

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

/**
 * Cached chapters (`manga_chapters`) and per-chapter reading state
 * (`manga_progress`). Progress is keyed by source, title and chapter URL and
 * is updated at most every few seconds by the reader.
 */
object MangaChapterStore {
    internal const val CHAPTERS = "manga_chapters"
    internal const val PROGRESS = "manga_progress"

    fun chapters(context: Context, sourceId: Long, mangaUrl: String): List<MangaChapter> =
        context.getKey<MangaChapterList>(CHAPTERS, mangaKey(sourceId, mangaUrl))?.chapters.orEmpty()

    fun saveChapters(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapters: List<MangaChapter>,
    ) {
        context.setKey(CHAPTERS, mangaKey(sourceId, mangaUrl), MangaChapterList(chapters))
    }

    fun allProgress(context: Context): List<MangaProgressRecord> =
        context.getKeys(PROGRESS)
            .mapNotNull { path -> context.getKey<MangaProgressRecord>(path) }
            .filter { it.chapterUrl.isNotBlank() }

    fun progress(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
    ): Map<String, ChapterProgress> =
        allProgress(context)
            .filter { it.sourceId == sourceId && it.mangaUrl == mangaUrl }
            .associate { it.chapterUrl to it.progress }

    fun progressFor(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
    ): ChapterProgress? =
        context.getKey<MangaProgressRecord>(
            PROGRESS,
            progressKey(sourceId, mangaUrl, chapterUrl),
        )?.progress

    fun saveProgress(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
        progress: ChapterProgress,
    ) {
        context.setKey(
            PROGRESS,
            progressKey(sourceId, mangaUrl, chapterUrl),
            MangaProgressRecord(sourceId, mangaUrl, chapterUrl, progress),
        )
    }

    fun setRead(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
        read: Boolean,
        at: Long = System.currentTimeMillis(),
    ) {
        val old = progressFor(context, sourceId, mangaUrl, chapterUrl) ?: ChapterProgress()
        saveProgress(
            context,
            sourceId,
            mangaUrl,
            chapterUrl,
            old.copy(
                read = read,
                lastPageRead = if (read) old.lastPageRead else 0,
                readAt = if (read) at else old.readAt,
            ),
        )
    }

    fun setBookmarked(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapterUrl: String,
        bookmarked: Boolean,
    ) {
        val old = progressFor(context, sourceId, mangaUrl, chapterUrl) ?: ChapterProgress()
        saveProgress(context, sourceId, mangaUrl, chapterUrl, old.copy(bookmarked = bookmarked))
    }

    /** Marks every chapter before [chapterUrl] in reading order as read. */
    fun markPreviousRead(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapters: List<MangaChapter>,
        chapterUrl: String,
    ) {
        val ordered = readingOrder(chapters)
        val index = ordered.indexOfFirst { it.url == chapterUrl }
        if (index <= 0) return
        val now = System.currentTimeMillis()
        ordered.take(index).forEach { chapter ->
            setRead(context, sourceId, mangaUrl, chapter.url, read = true, at = now)
        }
    }

    /**
     * Moves read flags forward when a source changed a chapter's URL but kept
     * its number and scanlator. The old record is kept so history survives.
     */
    fun applyAliases(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        aliases: Map<String, String>,
    ) {
        aliases.forEach { (newUrl, oldUrl) ->
            val old = progressFor(context, sourceId, mangaUrl, oldUrl) ?: return@forEach
            val new = progressFor(context, sourceId, mangaUrl, newUrl)
            saveProgress(
                context,
                sourceId,
                mangaUrl,
                newUrl,
                old.copy(
                    lastPageRead = maxOf(old.lastPageRead, new?.lastPageRead ?: 0),
                    pageCount = maxOf(old.pageCount, new?.pageCount ?: 0),
                    bookmarked = old.bookmarked || (new?.bookmarked ?: false),
                ),
            )
        }
    }

    fun removeProgress(context: Context, sourceId: Long, mangaUrl: String) {
        context.getKeys(PROGRESS)
            .filter { path -> context.getKey<MangaProgressRecord>(path)?.let {
                it.sourceId == sourceId && it.mangaUrl == mangaUrl
            } == true }
            .forEach { path -> context.removeKey(path) }
    }
}
