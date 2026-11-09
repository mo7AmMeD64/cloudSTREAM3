package com.kodastream.app.manga

import android.content.Context
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStore.setKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cached chapters (`manga_chapters`) and per-chapter reading state
 * (`manga_progress`). Progress is keyed by source, title and chapter URL and
 * is updated at most every few seconds by the reader.
 */
object MangaChapterStore {
    internal const val CHAPTERS = "manga_chapters"
    internal const val PROGRESS = "manga_progress"

    /** Bumped on every write so open screens refresh read marks. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    // getKeys() copies every preference in the app, so the progress key list
    // is read once and kept in sync by the writers below.
    private val progressKeys = HashSet<String>()
    private var progressKeysLoaded = false

    fun chapters(context: Context, sourceId: Long, mangaUrl: String): List<MangaChapter> =
        context.getKey<MangaChapterList>(CHAPTERS, mangaKey(sourceId, mangaUrl))?.chapters.orEmpty()

    fun saveChapters(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
        chapters: List<MangaChapter>,
    ) {
        context.setKey(CHAPTERS, mangaKey(sourceId, mangaUrl), MangaChapterList(chapters))
        _revision.value++
    }

    fun allProgress(context: Context): List<MangaProgressRecord> =
        keysMatching(context, "$PROGRESS/")
            .mapNotNull { path -> context.getKey<MangaProgressRecord>(path) }
            .filter { it.chapterUrl.isNotBlank() }

    fun progress(
        context: Context,
        sourceId: Long,
        mangaUrl: String,
    ): Map<String, ChapterProgress> {
        val prefix = "$PROGRESS/${mangaKey(sourceId, mangaUrl)}|"
        return keysMatching(context, prefix)
            .mapNotNull { path -> context.getKey<MangaProgressRecord>(path) }
            .associate { it.chapterUrl to it.progress }
    }

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
        val key = progressKey(sourceId, mangaUrl, chapterUrl)
        context.setKey(
            PROGRESS,
            key,
            MangaProgressRecord(sourceId, mangaUrl, chapterUrl, progress),
        )
        noteKeys(listOf("$PROGRESS/$key"))
        _revision.value++
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
        val now = System.currentTimeMillis()
        val existing = progress(context, sourceId, mangaUrl)
        val updates = chaptersBefore(chapters, chapterUrl).associate { chapter ->
            val old = existing[chapter.url] ?: ChapterProgress()
            progressKey(sourceId, mangaUrl, chapter.url) to MangaProgressRecord(
                sourceId = sourceId,
                mangaUrl = mangaUrl,
                chapterUrl = chapter.url,
                progress = old.copy(read = true, readAt = now),
            )
        }
        if (updates.isEmpty()) return
        context.setKeys(PROGRESS, updates)
        noteKeys(updates.keys.map { "$PROGRESS/$it" })
        _revision.value++
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
        val prefix = "$PROGRESS/${mangaKey(sourceId, mangaUrl)}|"
        keysMatching(context, prefix).forEach { path -> context.removeKey(path) }
        forgetKeys(prefix)
        _revision.value++
    }

    private fun keysMatching(context: Context, prefix: String): List<String> =
        synchronized(progressKeys) {
            if (!progressKeysLoaded) {
                progressKeys.addAll(context.getKeys(PROGRESS))
                progressKeysLoaded = true
            }
            progressKeys.filter { it.startsWith(prefix) }
        }

    private fun noteKeys(paths: Collection<String>) = synchronized(progressKeys) {
        if (progressKeysLoaded) progressKeys.addAll(paths)
    }

    private fun forgetKeys(prefix: String) = synchronized(progressKeys) {
        if (progressKeysLoaded) progressKeys.removeAll { it.startsWith(prefix) }
    }
}
