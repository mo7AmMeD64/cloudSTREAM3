package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.translate.GeminiTranslator
import com.kodastream.app.manga.translate.PageTranslation
import com.kodastream.app.manga.translate.TranslatedBlock
import com.kodastream.app.manga.translate.TranslationCache
import com.kodastream.app.manga.translate.TranslationException
import com.kodastream.app.manga.translate.moveToPage
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Translates pages one at a time, because the free Gemini tier is limited per
 * minute and per day. Finished pages live in memory and on disk, so stopping
 * at a quota error and retrying later only sends what is still missing.
 */
class TranslationController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentSettings: () -> MangaReaderSettings,
) {
    data class State(
        val translations: Map<String, PageTranslation> = emptyMap(),
        val visible: Boolean = true,
        val running: Boolean = false,
        val status: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val translator = GeminiTranslator()
    private var job: Job? = null

    /** Stable per displayed page; the two halves of a split spread are different pages. */
    fun pageKey(page: ReaderPage): String =
        page.chapter.chapter.url + "#" + page.index + if (page is InsertPage) "b" else "a"

    fun translatePage(page: ReaderPage) {
        if (_state.value.running) return
        start(listOf(page))
    }

    fun translateChapter(pages: List<ReaderPage>) {
        if (_state.value.running || pages.isEmpty()) return
        // Split spreads add their second half while paging; that half translates
        // when opened, since it is not in the chapter's page list.
        start(pages)
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, status = null) }
    }

    fun setVisible(visible: Boolean) {
        _state.update { it.copy(visible = visible) }
    }

    private fun start(pages: List<ReaderPage>) {
        val apiKey = AppPrefs.translationApiKey(context)
        if (apiKey.isNullOrBlank()) {
            _state.update { it.copy(status = context.getString(R.string.manga_translation_no_key)) }
            return
        }
        job = scope.launch { translatePages(pages, apiKey) }
    }

    private suspend fun translatePages(pages: List<ReaderPage>, apiKey: String) {
        val target = AppPrefs.translationTarget(context)
        val model = AppPrefs.translationModel(context)
        val total = pages.size
        _state.update { it.copy(running = true, status = progress(1, total)) }
        try {
            pages.forEachIndexed { index, page ->
                currentCoroutineContext().ensureActive()
                val key = pageKey(page)
                if (_state.value.translations.containsKey(key)) return@forEachIndexed
                _state.update { it.copy(status = progress(index + 1, total)) }

                if (!ensureLoaded(page)) return@forEachIndexed
                if (page.animated) return@forEachIndexed
                val file = page.file ?: return@forEachIndexed

                val variant = variant(page)
                val (cached, cacheKey) = withContext(Dispatchers.IO) {
                    val key = TranslationCache.key(fileHash(file), variant, target, model)
                    TranslationCache.read(context, key) to key
                }
                val translation = cached ?: withContext(Dispatchers.IO) {
                    val result = if (currentSettings().mode.isPaged) {
                        translatePaged(page, file, apiKey, model, target)
                    } else {
                        translateWebtoon(file, apiKey, model, target)
                    }
                    if (result.blocks.isNotEmpty()) {
                        TranslationCache.write(context, cacheKey, result)
                    }
                    result
                }
                if (translation.blocks.isNotEmpty()) {
                    _state.update {
                        it.copy(translations = it.translations + (key to translation))
                    }
                }
            }
            _state.update { it.copy(status = context.getString(R.string.manga_translation_done)) }
            delay(DONE_VISIBLE_MS)
            _state.update { it.copy(status = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TranslationException) {
            _state.update { it.copy(status = errorStatus(e)) }
        } catch (_: Throwable) {
            _state.update { it.copy(status = context.getString(R.string.manga_translation_failed)) }
        } finally {
            _state.update { it.copy(running = false) }
        }
    }

    /** A chapter run starts at the current page, so later pages still need downloading. */
    private suspend fun ensureLoaded(page: ReaderPage): Boolean {
        if (page.file?.isFile == true) return true
        val loader = page.chapter.pageLoader ?: return false
        if (page.status !is Page.State.Ready) {
            runCatching { loader.loadPage(page) }
            val state = withTimeoutOrNull(PAGE_LOAD_TIMEOUT_MS) {
                page.statusFlow.first {
                    it is Page.State.Ready || it is Page.State.Error
                }
            }
            if (state !is Page.State.Ready) return false
        }
        return page.file?.isFile == true
    }

    private suspend fun translatePaged(
        page: ReaderPage,
        file: File,
        apiKey: String,
        model: String,
        target: String,
    ): PageTranslation {
        val settings = currentSettings()
        val bitmap = withContext(Dispatchers.Default) {
            val source: BufferedSource = file.source().buffer()
            try {
                val processed = settings.asDualPageSettings().processPagedImage(
                    page = page,
                    imageSource = source,
                    isLeftToRight = !settings.mode.readingRtl,
                    onPageSplit = {},
                )
                decodeScaled(processed)
            } finally {
                source.close()
            }
        }
        try {
            val blocks = translator.translate(bitmap, target, apiKey, model)
            delay(REQUEST_SPACING_MS)
            return PageTranslation(blocks = blocks)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * A webtoon page can be taller than any model accepts, so it is sent as
     * slices. Each slice spans the full width, which keeps x coordinates
     * unchanged when the boxes move into the full page.
     */
    private suspend fun translateWebtoon(
        file: File,
        apiKey: String,
        model: String,
        target: String,
    ): PageTranslation {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw TranslationException(0, "Unreadable page")

        val sliceHeight = (width * 1.6f).roundToInt().coerceIn(1024, 2048)
        var sample = 1
        while (width / (sample * 2) >= MAX_EDGE / 2) sample *= 2
        val region = BitmapRegionDecoder.newInstance(file.path, false)
            ?: throw TranslationException(0, "Unreadable page")
        val blocks = mutableListOf<TranslatedBlock>()
        try {
            var top = 0
            while (top < height) {
                currentCoroutineContext().ensureActive()
                val bottom = min(height, top + sliceHeight)
                val bitmap = region.decodeRegion(
                    Rect(0, top, width, bottom),
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: throw TranslationException(0, "Unreadable page")
                val slice = try {
                    translator.translate(bitmap, target, apiKey, model)
                } finally {
                    bitmap.recycle()
                }
                slice.mapNotNullTo(blocks) { it.moveToPage(top, bottom - top, height) }
                top = bottom
                // Space every request, including the last slice, so the next
                // page does not start inside the same free-tier minute.
                delay(REQUEST_SPACING_MS)
            }
        } finally {
            region.recycle()
        }
        return PageTranslation(blocks = blocks)
    }

    private fun decodeScaled(source: BufferedSource): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(source.peek().inputStream(), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw TranslationException(0, "Unreadable page")
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeStream(
            source.inputStream(),
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw TranslationException(0, "Unreadable page")
        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_EDGE) return decoded
        val ratio = MAX_EDGE.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).roundToInt().coerceAtLeast(1),
            (decoded.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun variant(page: ReaderPage): String {
        val settings = currentSettings()
        if (!settings.mode.isPaged) return "w"
        return "p${settings.dualPageSplit}${settings.dualPageInvert}" +
            "${settings.dualPageRotateToFit}${settings.dualPageRotateToFitInvert}" +
            "${settings.mode.readingRtl}${page is InsertPage}"
    }

    private fun fileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun progress(current: Int, total: Int): String =
        context.getString(R.string.manga_translation_progress, current, total)

    private fun errorStatus(e: TranslationException): String = when (e.code) {
        401, 403 -> context.getString(R.string.manga_translation_bad_key)
        404 -> context.getString(R.string.manga_translation_bad_model)
        429 -> context.getString(R.string.manga_translation_quota)
        else -> e.message ?: context.getString(R.string.manga_translation_failed)
    }

    private companion object {
        const val MAX_EDGE = 1600
        const val REQUEST_SPACING_MS = 4_000L
        const val DONE_VISIBLE_MS = 2_500L
        const val PAGE_LOAD_TIMEOUT_MS = 90_000L
    }
}
