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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonStripImageView.kt`. A tall page
 * is drawn as a stack of decoded strips, so an 800 x 20000 page never becomes
 * one GPU texture. Strips are decoded from the disk cache at a sample size
 * that keeps each strip around 1024 px tall on screen.
 *
 * The Mihon decoder (`ca.mpreg:imagedecoder`) is not bundled here; this uses
 * [BitmapRegionDecoder] and falls back to a sampled [BitmapFactory] decode for
 * formats it cannot region-decode. See the report for that one fallback.
 */
class WebtoonStripImageView(context: Context) : View(context) {

    private var strips: List<Bitmap> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clipRect = Rect()
    private val dstRect = Rect()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var decodeJob: Job? = null

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
        imageWidth = 0
        imageHeight = 0
        decodeJob = scope.launch {
            try {
                val width = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val decoded = withContext(Dispatchers.IO) { decode(file, max(1, width)) }
                strips = decoded.strips
                imageWidth = decoded.width
                imageHeight = decoded.height
                onLoaded?.invoke()
                requestLayout()
                invalidate()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                onError?.invoke(e)
            }
        }
    }

    fun recycle() {
        decodeJob?.cancel()
        scope.cancel()
        releaseStrips()
        onLoaded = null
        onError = null
        imageWidth = 0
        imageHeight = 0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (imageWidth > 0) (width.toLong() * imageHeight / imageWidth).toInt() else 0
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (imageWidth <= 0) return
        canvas.getClipBounds(clipRect)
        val scale = width.toFloat() / imageWidth
        var srcTop = 0
        for (strip in strips) {
            val top = (srcTop * scale).roundToInt()
            srcTop += strip.height
            val bottom = (srcTop * scale).roundToInt()
            if (bottom <= clipRect.top) continue
            if (top >= clipRect.bottom) break
            dstRect.set(0, top, width, bottom)
            canvas.drawBitmap(strip, null, dstRect, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    private fun releaseStrips() {
        val released = strips
        strips = emptyList()
        released.forEach(Bitmap::recycle)
    }

    private data class Strips(val strips: List<Bitmap>, val width: Int, val height: Int)

    private fun decode(file: File, targetWidth: Int): Strips {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Unreadable page image")
        }
        // Keep the cached image close to screen resolution; tiled decoding then
        // only pays for what is visible.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2

        val region = try {
            BitmapRegionDecoder.newInstance(file.path, false)
        } catch (_: Throwable) {
            null
        }

        if (region != null) {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val scaledWidth = bounds.outWidth / sample
            val scaledHeight = bounds.outHeight / sample
            // One output strip is at most this many source rows.
            val stripSource = max(1, (1024 * sample))
            val result = ArrayList<Bitmap>()
            var y = 0
            try {
                while (y < bounds.outHeight) {
                    val height = minOf(stripSource, bounds.outHeight - y)
                    val bitmap = region.decodeRegion(Rect(0, y, bounds.outWidth, y + height), options)
                        ?: throw IllegalStateException("Unreadable page region")
                    result.add(bitmap)
                    y += height
                }
            } finally {
                region.recycle()
            }
            return Strips(result, scaledWidth, scaledHeight)
        }

        // Formats without a region decoder (some WebP/AVIF on old APIs) still
        // need a bounded bitmap, so sample until the height is manageable.
        var fallbackSample = sample
        while (bounds.outWidth / fallbackSample > 4096 || bounds.outHeight / fallbackSample > 12288) {
            fallbackSample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = fallbackSample }
        val bitmap = BitmapFactory.decodeFile(file.path, options)
            ?: throw IllegalStateException("Unreadable page image")
        return Strips(listOf(bitmap), bitmap.width, bitmap.height)
    }
}
