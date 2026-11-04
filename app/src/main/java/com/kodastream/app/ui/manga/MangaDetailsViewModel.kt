package com.kodastream.app.ui.manga

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.manga.ChapterProgress
import com.kodastream.app.manga.ChapterSyncResult
import com.kodastream.app.manga.MangaCategoryStore
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaChapterStore
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.MangaLibraryStore
import com.kodastream.app.manga.continueReading
import com.kodastream.app.manga.extension.LoadedSource
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.key
import com.kodastream.app.manga.mangaKey
import com.kodastream.app.manga.nameOrNull
import com.kodastream.app.manga.rootMessage
import com.kodastream.app.manga.syncChapters
import com.kodastream.app.manga.urlOrNull
import com.kodastream.app.manga.withRemoteDetails
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the details screen draws; cached values first, network later. */
data class MangaDetailsState(
    val entry: MangaEntry = MangaEntry(),
    val chapters: List<MangaChapter> = emptyList(),
    val progress: Map<String, ChapterProgress> = emptyMap(),
    val favorite: Boolean = false,
    val categoryId: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val sourceAvailable: Boolean = true,
)

/**
 * Details keeps whatever it has on screen when a refresh fails, and only
 * writes the library record when the title is a favourite, so browsing never
 * fills the library by itself.
 */
class MangaDetailsViewModel(
    application: Application,
    private val ref: MangaRef,
) : AndroidViewModel(application) {
    private val app = application
    private val manager = MangaSourceManager.get(app)
    private val source: CatalogueSource? =
        manager.catalogueSourceById(ref.sourceId) as? CatalogueSource
    private val loaded: LoadedSource? =
        manager.sources.value.firstOrNull { it.source.id == ref.sourceId }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<MangaDetailsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh(first = true) }
    }

    private fun initialState(): MangaDetailsState {
        val cached = MangaLibraryStore.entry(app, ref.sourceId, ref.url)
            ?: MangaDetailsCache.get(ref.key())
            ?: ref.toEntry(source?.name.orEmpty(), loaded?.language.orEmpty())
        val chapters = MangaChapterStore.chapters(app, ref.sourceId, ref.url)
        return MangaDetailsState(
            entry = cached,
            chapters = chapters,
            progress = MangaChapterStore.progress(app, ref.sourceId, ref.url),
            favorite = MangaLibraryStore.isFavorite(app, ref.sourceId, ref.url),
            categoryId = MangaCategoryStore.categoryOf(app, ref.sourceId, ref.url),
            loading = chapters.isEmpty(),
            sourceAvailable = source != null,
        )
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch { refresh(first = false) }
    }

    private suspend fun refresh(first: Boolean) {
        val catalogue = source ?: run {
            _state.update {
                it.copy(loading = false, refreshing = false, sourceAvailable = false)
            }
            return
        }
        _state.update {
            it.copy(
                loading = first && it.chapters.isEmpty(),
                refreshing = true,
                error = null,
            )
        }
        val before = _state.value
        val update = try {
            val cached = before.chapters.map { it.toSChapter() }
            withContext(Dispatchers.IO) {
                catalogue.getMangaUpdate(
                    manga = before.entry.toSManga(),
                    chapters = cached,
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update {
                it.copy(loading = false, refreshing = false, error = rootMessage(e))
            }
            return
        }

        // Fetched chapters are shown before the details are mapped: a bad
        // remote object can then never hide the list the owner waited for.
        try {
            val fetched = update.chapters.mapIndexedNotNull { index, chapter ->
                chapter.toModel(index)
            }
            val sync: ChapterSyncResult = syncChapters(before.chapters, fetched)
            MangaChapterStore.saveChapters(app, ref.sourceId, ref.url, sync.chapters)
            MangaChapterStore.applyAliases(app, ref.sourceId, ref.url, sync.aliases)
            _state.update {
                it.copy(
                    chapters = sync.chapters,
                    progress = MangaChapterStore.progress(app, ref.sourceId, ref.url),
                    loading = false,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(error = rootMessage(e)) }
        }

        try {
            val favorite = MangaLibraryStore.isFavorite(app, ref.sourceId, ref.url)
            val entry = before.entry.withRemoteDetails(update.manga, favorite)
                .copy(
                    addedAt = before.entry.addedAt,
                    lastReadAt = before.entry.lastReadAt,
                    lastChecked = System.currentTimeMillis(),
                )
            MangaDetailsCache.put(ref.key(), entry)
            if (favorite) {
                MangaLibraryStore.mergeDetails(app, entry)
            }
            _state.update {
                it.copy(entry = entry, favorite = favorite)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _state.update { it.copy(error = it.error ?: rootMessage(e)) }
        }

        _state.update { it.copy(loading = false, refreshing = false) }
    }

    fun toggleFavorite() {
        val current = _state.value
        if (current.favorite) {
            MangaLibraryStore.remove(app, ref.sourceId, ref.url)
            _state.update { it.copy(favorite = false, categoryId = null) }
        } else {
            MangaLibraryStore.add(app, ref)
            if (current.entry.title.isNotBlank()) {
                MangaLibraryStore.mergeDetails(app, current.entry)
            }
            _state.update {
                it.copy(
                    favorite = true,
                    categoryId = MangaCategoryStore.categoryOf(app, ref.sourceId, ref.url),
                )
            }
        }
    }

    fun moveToCategory(categoryId: String?) {
        MangaCategoryStore.move(app, listOf(ref.key()), categoryId)
        _state.update { it.copy(categoryId = categoryId) }
    }

    fun setChapterRead(chapterUrl: String, read: Boolean) {
        MangaChapterStore.setRead(app, ref.sourceId, ref.url, chapterUrl, read)
        refreshProgress()
    }

    fun markPreviousRead(chapterUrl: String) {
        MangaChapterStore.markPreviousRead(
            app,
            ref.sourceId,
            ref.url,
            _state.value.chapters,
            chapterUrl,
        )
        refreshProgress()
    }

    fun toggleBookmark(chapterUrl: String) {
        val current = _state.value.progress[chapterUrl]?.bookmarked == true
        MangaChapterStore.setBookmarked(app, ref.sourceId, ref.url, chapterUrl, !current)
        refreshProgress()
    }

    /** Called when returning from the reader so the marks match the store. */
    fun refreshProgress() {
        _state.update {
            it.copy(progress = MangaChapterStore.progress(app, ref.sourceId, ref.url))
        }
    }

    /** The Start/Continue button target, recomputed from the live list. */
    fun continueTarget() = continueReading(_state.value.chapters, _state.value.progress)

    fun sourceName(): String =
        _state.value.entry.sourceName.ifBlank { loaded?.source?.name.orEmpty() }

    fun shareUrl(): String? {
        val http = source as? HttpSource ?: return null
        return runCatching { http.getMangaUrl(_state.value.entry.toSManga()) }.getOrNull()
    }

    fun readingMode(): com.kodastream.app.manga.MangaReadingMode =
        MangaPrefs.readerModeFor(app, ref.sourceId, ref.url)
}

/** A small in-memory LRU of recently opened details, newest last. */
internal object MangaDetailsCache {
    private const val LIMIT = 20
    private val entries = object : LinkedHashMap<String, MangaEntry>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MangaEntry>?) =
            size > LIMIT
    }

    @Synchronized
    fun get(key: String): MangaEntry? = entries[key]?.copy()

    @Synchronized
    fun put(key: String, entry: MangaEntry) {
        entries[key] = entry.copy()
    }
}

internal fun MangaRef.toEntry(sourceName: String, language: String): MangaEntry = MangaEntry(
    sourceId = sourceId,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    sourceName = sourceName,
    language = language,
)

internal fun MangaEntry.toSManga(): SManga = SManga.create().also { manga ->
    manga.url = url
    manga.title = title
    manga.thumbnail_url = thumbnailUrl
    manga.author = author
    manga.artist = artist
    manga.description = description
    manga.genre = genres.joinToString(", ")
    manga.status = status
    manga.update_strategy = runCatching {
        eu.kanade.tachiyomi.source.model.UpdateStrategy.valueOf(updateStrategy)
    }.getOrDefault(eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE)
    if (memo.isNotBlank()) {
        runCatching {
            manga.memo = kotlinx.serialization.json.Json.parseToJsonElement(memo)
                .let { it as kotlinx.serialization.json.JsonObject }
        }
    }
    manga.initialized = true
}

internal fun SChapter.toModel(sourceOrder: Int): MangaChapter? {
    val chapterUrl = urlOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val chapterName = nameOrNull()?.takeIf { it.isNotBlank() }
        ?: chapterFallbackName(chapter_number, chapterUrl)
    return MangaChapter(
        url = chapterUrl,
        name = chapterName,
        number = chapter_number,
        scanlator = scanlator,
        dateUpload = date_upload,
        sourceOrder = sourceOrder,
        memo = memo.toString(),
    )
}

private fun chapterFallbackName(number: Float, url: String): String {
    if (number >= 0f) {
        return if (number % 1f == 0f) number.toInt().toString() else number.toString()
    }
    return url.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: url
}

internal fun MangaChapter.toSChapter(): SChapter = SChapter.create().also { chapter ->
    chapter.url = url
    chapter.name = name
    chapter.chapter_number = number
    chapter.scanlator = scanlator
    chapter.date_upload = dateUpload
    if (memo.isNotBlank() && memo != "{}") {
        runCatching {
            chapter.memo = kotlinx.serialization.json.Json.parseToJsonElement(memo)
                .let { it as kotlinx.serialization.json.JsonObject }
        }
    }
}
