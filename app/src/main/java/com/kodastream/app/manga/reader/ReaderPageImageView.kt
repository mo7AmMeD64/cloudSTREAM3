package com.kodastream.app.manga.reader

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import com.kodastream.app.manga.MangaImageLoader
import okio.BufferedSource
import java.io.File
import java.nio.ByteBuffer

/**
 * Ported from Mihon's `viewer/ReaderPageImageView.kt`. A [FrameLayout] that
 * swaps between [SubsamplingScaleImageView] for static pages, [PhotoView] for
 * animated ones, and [WebtoonStripImageView] for tall webtoon pages.
 *
 * The pager gives it a [BufferedSource] so the wide-page split/rotate pass has
 * already run; the webtoon gives it the cached file because the strip decoder
 * needs random access to the page.
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context) {

    private var pageView: View? = null
    private var animatedRequest: Disposable? = null

    private var config: Config? = null

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: ((Throwable?) -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * A pager page. The image has already been split or rotated if the viewer
     * asked for it, so [source] is what [SubsamplingScaleImageView] decodes.
     */
    fun setImage(page: ReaderPage, source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(page.file ?: ByteBuffer.wrap(source.readByteArray()), config)
        } else if (isWebtoon) {
            prepareStripImageView()
            page.file?.let { setStripImage(it) }
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    /** A webtoon page, drawn as strips from its cached file. */
    fun setImage(file: File, isAnimated: Boolean, config: Config) {
        this.config = config
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(file, config)
        } else {
            prepareStripImageView()
            setStripImage(file)
        }
    }

    /**
     * Called when the pager selects this page. A wide page in landscape gets
     * Mihon's "zoom to fit height" treatment, from the edge the page opens at.
     */
    fun onPageSelected(forward: Boolean) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (view.isReady) {
            view.landscapeZoom(forward)
        } else {
            view.setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        view.setupZoom(config)
                        view.landscapeZoom(forward)
                        onImageLoaded?.invoke()
                    }

                    override fun onImageLoadError(e: Exception) {
                        onImageLoadError?.invoke(e)
                    }
                },
            )
        }
    }

    open fun recycle() {
        animatedRequest?.dispose()
        animatedRequest = null
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is AppCompatImageView -> it.setImageDrawable(null)
                is WebtoonStripImageView -> it.recycle()
            }
            it.isVisible = false
        }
    }

    fun canPanLeft(): Boolean = canPan { it.left }

    fun canPanRight(): Boolean = canPan { it.right }

    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
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
            view.animateCenter(target)
                ?.withEasing(EASE_OUT_QUAD)
                ?.withDuration(250)
                ?.withInterruptible(true)
                ?.start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = SubsamplingScaleImageView(context).apply {
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged?.invoke(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) = Unit
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked?.invoke() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> Unit
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        val config = config ?: return
        if (!config.landscapeZoom ||
            config.minimumScaleType != SCALE_TYPE_CENTER_INSIDE ||
            sWidth <= sHeight ||
            scale != minScale
        ) {
            return
        }
        handler?.postDelayed({
            if (!isReady) return@postDelayed
            val point = when (config.zoomStartPosition) {
                ZoomStartPosition.LEFT -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                ZoomStartPosition.RIGHT -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                ZoomStartPosition.CENTER -> center
            }
            val targetScale = height.toFloat() / sHeight.toFloat()
            animateScaleAndCenter(targetScale, point)
                ?.withDuration(500)
                ?.withEasing(EASE_IN_OUT_QUAD)
                ?.withInterruptible(true)
                ?.start()
        }, 500)
    }

    private fun setNonAnimatedImage(data: BufferedSource, config: Config) =
        (pageView as? SubsamplingScaleImageView)?.apply {
            setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
            setMinimumScaleType(config.minimumScaleType)
            // Just so that a very small image is fit on its first load.
            setMinimumDpi(1)
            setCropBorders(config.cropBorders)
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        setupZoom(config)
                        if (isVisibleOnScreen()) landscapeZoom(true)
                        this@ReaderPageImageView.onImageLoaded?.invoke()
                    }

                    override fun onImageLoadError(e: Exception) {
                        this@ReaderPageImageView.onImageLoadError?.invoke(e)
                    }
                },
            )
            setImage(ImageSource.inputStream(data.inputStream()))
            isVisible = true
        }

    private fun prepareStripImageView() {
        if (pageView is WebtoonStripImageView) return
        removeView(pageView)
        pageView = WebtoonStripImageView(context)
        addView(pageView, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun setStripImage(file: File) {
        val view = pageView as? WebtoonStripImageView ?: return
        view.onLoaded = {
            view.isVisible = true
            onImageLoaded?.invoke()
        }
        view.onError = { onImageLoadError?.invoke(it) }
        view.setImage(file)
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force exactly two scale levels on double tap.
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked?.invoke()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged?.invoke(scale)
                }
            }
        }
        addView(
            pageView,
            MATCH_PARENT,
            if (isWebtoon) WRAP_CONTENT else MATCH_PARENT,
        )
    }

    private fun setAnimatedImage(data: Any, config: Config) =
        (pageView as? AppCompatImageView)?.apply {
            if (this is PhotoView) {
                setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
            }

            animatedRequest?.dispose()
            val request = ImageRequest.Builder(context)
                .data(data)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .listener(
                    onSuccess = { _, result ->
                        val drawable = result.drawable
                        setImageDrawable(drawable)
                        (drawable as? Animatable)?.start()
                        isVisible = true
                        this@ReaderPageImageView.onImageLoaded?.invoke()
                    },
                    onError = { _, result ->
                        this@ReaderPageImageView.onImageLoadError?.invoke(result.throwable)
                    },
                )
                .crossfade(false)
                .build()
            animatedRequest = MangaImageLoader.get(context).enqueue(request)
        }

    private fun Int.getSystemScaledDuration(): Int {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        return (this * scale).toInt().coerceAtLeast(1)
    }

    private fun View.isVisibleOnScreen(): Boolean = isShown && getGlobalVisibleRect(Rect())

    /**
     * All of the config except [zoomDuration] is only used for non-animated
     * images; an animated page keeps its own intrinsic scale.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }
}

private const val MAX_ZOOM_SCALE = 5F
