package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonStripImageView.kt`. A tall page
 * is drawn as a stack of decoded strips, so an 800 x 20000 page never becomes
 * one GPU texture.
 *
 * Strips are decoded progressively: the page's height is known after the
 * header pass, the area near the current scroll position is decoded first,
 * and each finished strip is drawn immediately. The old version decoded the
 * whole page before showing anything, which is what made webtoon pages feel
 * slow on long chapters.
 */
class WebtoonStripImageView(context: Context) : View(context) {

    private var strips: Array<Bitmap?> = emptyArray()
    private var imageWidth = 0
    private var imageHeight = 0
    private var sourceRowsPerStrip = 0
    private var sampleSize = 1
    private var sourceFile: File? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clipRect = Rect()
    private val dstRect = Rect()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var decodeJob: Job? = null

    /** The strip nearest the viewport when decoding started. */
    private var firstVisibleStrip = 0
    private var isReleasedOffScreen = false

    var colorFilter: ColorFilter?
        get() = paint.colorFilter
        set(value) {
            paint.colorFilter = value
            invalidate()
        }

    var onLoaded: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    fun setImage(file: File) {
        decodeJob?.cancel()
        releaseStrips()
        sourceFile = file
        imageWidth = 0
        imageHeight = 0
        isReleasedOffScreen = false
        decodeJob = scope.launch {
            try {
                val width = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val bounds = withContext(Dispatchers.IO) { readBounds(file) }
                if (bounds.width <= 0 || bounds.height <= 0) {
                    throw IllegalStateException("Unreadable page image")
                }
                var sample = 1
                while (bounds.width / (sample * 2) >= max(1, width)) sample *= 2
                val stripRows = max(1, STRIP_BYTES / (bounds.width / sample).coerceAtLeast(1) / 4)
                strips = arrayOfNulls((bounds.height + stripRows - 1) / stripRows)
                imageWidth = bounds.width
                imageHeight = bounds.height
                sampleSize = sample
                sourceRowsPerStrip = stripRows
                firstVisibleStrip = stripNearest()
                requestLayout()
                invalidate()

                val region = withContext(Dispatchers.IO) {
                    runCatching { BitmapRegionDecoder.newInstance(file.path, false) }.getOrNull()
                }
                if (region == null) {
                    withContext(Dispatchers.IO) { decodeFallback(file) }
                    onLoaded?.invoke()
                    requestLayout()
                    invalidate()
                    return@launch
                }
                var first = true
                try {
                    val order = stripOrder(firstVisibleStrip)
                    for (index in order) {
                        if (!isActive) return@launch
                        val bitmap = withContext(Dispatchers.IO) { decodeRegion(region, index) } ?: continue
                        if (!isActive) {
                            bitmap.recycle()
                            return@launch
                        }
                        strips[index] = bitmap
                        if (first) {
                            first = false
                            // The page is on screen as soon as its first strip
                            // is, instead of after every strip decoded.
                            onLoaded?.invoke()
                        }
                        invalidate()
                    }
                } finally {
                    withContext(Dispatchers.IO) { region.recycle() }
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onError?.invoke(e)
            }
        }
    }

    fun recycle() {
        decodeJob?.cancel()
        decodeJob = null
        releaseStrips()
        sourceFile = null
        onLoaded = null
        onError = null
        imageWidth = 0
        imageHeight = 0
        isReleasedOffScreen = false
        requestLayout()
        // The scope is deliberately kept: RecyclerView recycles the same view
        // for another page and a cancelled scope would never decode again.
    }

    // The viewer keeps a few pages off screen without recycling them, which
    // adds up for huge pages. The height stays known, so the layout does not
    // move while the page decodes again on the way back.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        val used = strips.sumOf { it?.allocationByteCount?.toLong() ?: 0L }
        if (used > LARGE_PAGE_BYTES) {
            decodeJob?.cancel()
            releaseStrips(keepLayout = true)
            isReleasedOffScreen = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isReleasedOffScreen) {
            isReleasedOffScreen = false
            sourceFile?.let(::setImage)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (imageWidth > 0) (imageHeight * renderScale(width)).roundToInt() else 0
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (imageWidth <= 0 || sourceRowsPerStrip <= 0) return
        canvas.getClipBounds(clipRect)
        val scale = renderScale(width)
        val drawnWidth = (imageWidth * scale).roundToInt()
        val left = (width - drawnWidth) / 2
        strips.forEachIndexed { index, strip ->
            if (strip == null) return@forEachIndexed
            val srcTop = index * sourceRowsPerStrip
            val top = (srcTop * scale).roundToInt()
            val bottom = ((srcTop + sourceRowsPerStrip).toLong() * scale).toInt()
            if (bottom <= clipRect.top) return@forEachIndexed
            if (top >= clipRect.bottom) return@forEachIndexed
            dstRect.set(left, top, left + drawnWidth, bottom)
            canvas.drawBitmap(strip, null, dstRect, paint)
        }
    }

    /**
     * Never enlarge past the page's own pixels: filling the width upscaled
     * small pages, which is what made webtoon pages look soft. Wider pages
     * still scale down to the view, now centred instead of stretched.
     */
    private fun renderScale(viewWidth: Int): Float =
        if (imageWidth <= 0 || viewWidth <= 0) 1f
        else min(1f, viewWidth.toFloat() / imageWidth)

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun readBounds(file: File): IntSize {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return IntSize(bounds.outWidth, bounds.outHeight)
    }

    private fun decodeRegion(region: BitmapRegionDecoder, index: Int): Bitmap? {
        val top = index * sourceRowsPerStrip
        if (top >= imageHeight) return null
        val bottom = minOf(top + sourceRowsPerStrip, imageHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return runCatching {
            region.decodeRegion(Rect(0, top, imageWidth, bottom), options)
        }.getOrNull()
    }

    /** The visible area, then the rest in reading order, then upwards. */
    private fun stripOrder(from: Int): List<Int> {
        if (strips.isEmpty()) return emptyList()
        val start = from.coerceIn(0, strips.lastIndex)
        val order = ArrayList<Int>(strips.size)
        for (index in start..strips.lastIndex) order.add(index)
        for (index in start - 1 downTo 0) order.add(index)
        return order
    }

    private fun stripNearest(): Int {
        if (strips.isEmpty() || sourceRowsPerStrip <= 0 || imageWidth <= 0) return 0
        val scale = renderScale(width)
        val center = (clipRect.top + clipRect.bottom) / 2f
        if (scale <= 0f) return 0
        val sourceRow = (center / scale).toInt()
        return (sourceRow / sourceRowsPerStrip).coerceIn(0, strips.lastIndex)
    }

    private fun decodeFallback(file: File) {
        var fallback = sampleSize
        while (imageWidth / fallback > 4096 || imageHeight / fallback > 12288) fallback *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = fallback }
        val bitmap = BitmapFactory.decodeFile(file.path, options)
            ?: throw IllegalStateException("Unreadable page image")
        strips = arrayOf(bitmap)
        sourceRowsPerStrip = imageHeight
        invalidate()
    }

    private fun releaseStrips(keepLayout: Boolean = false) {
        val released = strips
        strips = emptyArray()
        released.forEach { it?.recycle() }
        if (!keepLayout) {
            imageWidth = 0
            imageHeight = 0
            sourceRowsPerStrip = 0
        }
    }

    private data class IntSize(val width: Int, val height: Int)

    private companion object {
        const val STRIP_BYTES = 2 * 1024 * 1024
        const val LARGE_PAGE_BYTES = 64L * 1024 * 1024
    }
}
