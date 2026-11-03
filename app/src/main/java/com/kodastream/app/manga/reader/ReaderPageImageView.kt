package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import coil.load
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.kodastream.app.manga.MangaImageLoader
import com.kodastream.app.manga.MangaPage
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.MangaScaleType
import com.kodastream.app.manga.MangaZoomStart
import java.io.File
import java.io.InputStream

/**
 * Ported from Mihon's `viewer/ReaderPageImageView.kt`: a [FrameLayout] that
 * swaps between [SubsamplingScaleImageView] for static pages, a Coil image for
 * animated ones, and [WebtoonStripImageView] for tall webtoon pages.
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context) {

    private var pageView: android.view.View? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    fun setPage(page: ReaderPage, settings: MangaReaderSettings, sourceId: Long) {
        val file = page.file ?: return
        if (isWebtoon) {
            prepareStripImageView()
            val strip = pageView as WebtoonStripImageView
            strip.onLoaded = {
                strip.isVisible = true
                onImageLoaded?.invoke()
            }
            strip.onError = { onImageLoadError?.invoke(it) }
            strip.setImage(file)
            return
        }

        if (isAnimated(file)) {
            prepareAnimatedImageView()
            val image = pageView as ImageView
            image.load(
                MangaPage(sourceId, page.chapter.chapter.url, page.index, page),
                MangaImageLoader.get(context),
            ) {
                listener(
                    onSuccess = { _, _ ->
                        image.isVisible = true
                        onImageLoaded?.invoke()
                    },
                    onError = { _, result ->
                        onImageLoadError?.invoke(result.throwable)
                    },
                )
            }
            return
        }

        prepareNonAnimatedImageView()
        val view = pageView as SubsamplingScaleImageView
        view.setMinimumScaleType(settings.scaleType.minimumScaleType())
        view.setDoubleTapZoomDuration(if (settings.pageTransitions) 200 else 0)
        view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
        view.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
        view.setMinimumTileDpi(180)
        view.setMinimumDpi(1)
        view.setCropBorders(settings.cropBorders)
        view.setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(view, settings)
                    view.isVisible = true
                    onImageLoaded?.invoke()
                }

                override fun onImageLoadError(e: Exception) {
                    onImageLoadError?.invoke(e)
                }
            },
        )
        view.setImage(ImageSource.uri(context, Uri.fromFile(file)))
    }

    open fun recycle() {
        when (val view = pageView) {
            is SubsamplingScaleImageView -> view.recycle()
            is WebtoonStripImageView -> view.recycle()
            is ImageView -> view.setImageDrawable(null)
        }
        pageView?.isVisible = false
        onImageLoaded = null
        onImageLoadError = null
        onViewClicked = null
        onScaleChanged = null
    }

    fun canPanLeft(): Boolean = canPan { it.left }

    fun canPanRight(): Boolean = canPan { it.right }

    private fun canPan(fn: (android.graphics.RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            android.graphics.RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    fun panLeft() = pan { center, view -> center.also { it.x -= view.width / view.scale } }

    fun panRight() = pan { center, view -> center.also { it.x += view.width / view.scale } }

    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_IN_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun setupZoom(view: SubsamplingScaleImageView, settings: MangaReaderSettings) {
        if (!view.isReady) return
        view.maxScale = view.scale * MAX_ZOOM_SCALE
        view.setDoubleTapZoomScale(if (settings.doubleTapZoom) view.scale * 2 else view.scale)
        val point = when (settings.zoomStart) {
            MangaZoomStart.LEFT -> PointF(0F, 0F)
            MangaZoomStart.RIGHT -> PointF(view.sWidth.toFloat(), 0F)
            MangaZoomStart.CENTER -> view.center
            MangaZoomStart.AUTOMATIC -> when {
                settings.mode.readingRtl -> PointF(view.sWidth.toFloat(), 0F)
                settings.mode.isPaged -> PointF(0F, 0F)
                else -> view.center
            }
        }
        view.setScaleAndCenter(view.scale, point)
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)
        pageView = SubsamplingScaleImageView(context).apply {
            setOnClickListener { onViewClicked?.invoke() }
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged?.invoke(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) = Unit
                },
            )
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)
        pageView = AppCompatImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { onViewClicked?.invoke() }
        }
        addView(pageView, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun prepareStripImageView() {
        if (pageView is WebtoonStripImageView) return
        removeView(pageView)
        pageView = WebtoonStripImageView(context)
        addView(pageView, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun isAnimated(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(64)
            val count = input.read(header)
            if (count >= 6 &&
                header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
            ) {
                return true
            }
            if (count >= 12 &&
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
            ) {
                val window = ByteArray(64 * 1024)
                var seen = count
                if (seen > 0) {
                    window.copyInto(window, 0, 0, count)
                    while (seen < window.size) {
                        val read = input.read(window, seen, window.size - seen)
                        if (read <= 0) break
                        seen += read
                    }
                    val text = String(window, 0, seen, Charsets.ISO_8859_1)
                    return text.contains("ANIM") || text.contains("ANMF")
                }
            }
        }
        false
    }.getOrDefault(false)

    private fun MangaScaleType.minimumScaleType(): Int = when (this) {
        MangaScaleType.FIT_SCREEN -> 1
        MangaScaleType.FIT_WIDTH -> 3
        MangaScaleType.FIT_HEIGHT -> 4
        MangaScaleType.ORIGINAL -> 5
        MangaScaleType.SMART_FIT -> 6
    }
}

private const val MAX_ZOOM_SCALE = 5f
