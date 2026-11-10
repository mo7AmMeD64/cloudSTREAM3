package com.kodastream.app.manga.reader

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonRecyclerView.kt`, `WebtoonFrame.kt`
 * and their gesture handling. Kept together because they only work as a pair.
 */
class WebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    var originalHeight = 0
        private set
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE
    var zoomOutDisabled = false
        set(value) {
            field = value
            if (value && currentScale < DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
            }
        }
    private val minRate get() = if (zoomOutDisabled) DEFAULT_RATE else MIN_RATE

    private val listener = GestureListener()
    private val detector = Detector()

    var sidePaddingPercent = 0
        set(value) {
            field = value
            x = getPositionX(x)
        }

    val sidePaddingPixels: Int
        get() = (Resources.getSystem().displayMetrics.widthPixels * (sidePaddingPercent / 100f)).toInt()

    var doubleTapZoom = true
    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    private var isManuallyScrolling = false
    private var tapDuringManualScroll = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        if (layoutParams?.height == MATCH_PARENT) {
            originalHeight = MeasureSpec.getSize(heightSpec)
        }
        super.onMeasure(widthSpec, heightSpec)
        x = getPositionX(x)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) tapDuringManualScroll = isManuallyScrolling
        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager as? LinearLayoutManager ?: return
        lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val visibleItemCount = layoutManager?.childCount ?: 0
        val totalItemCount = layoutManager?.itemCount ?: 0
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0
        if (state == SCROLL_STATE_IDLE) isManuallyScrolling = false
    }

    private fun getPositionX(positionX: Float): Float {
        if (currentScale < 1) return 0f
        val maxPositionX = ((halfWidth - sidePaddingPixels) * currentScale - halfWidth).coerceAtLeast(0f)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        if (currentScale < 1) return (originalHeight / 2 - halfHeight).toFloat()
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(fromRate: Float, toRate: Float, fromX: Float, toX: Float, fromY: Float, toY: Float) {
        isZooming = true
        val set = AnimatorSet()
        ValueAnimator.ofFloat(fromX, toX).apply {
            addUpdateListener { x = getPositionX(it.animatedValue as Float) }
            set.playTogether(this)
        }
        ValueAnimator.ofFloat(fromY, toY).apply {
            addUpdateListener { y = getPositionY(it.animatedValue as Float) }
            set.playTogether(this)
        }
        ValueAnimator.ofFloat(fromRate, toRate).apply {
            addUpdateListener {
                currentScale = it.animatedValue as Float
                setScaleRate(currentScale)
            }
            set.playTogether(this)
        }
        set.duration = ANIMATOR_DURATION_TIME.toLong()
        set.interpolator = DecelerateInterpolator()
        set.start()
        set.doOnEnd {
            isZooming = false
            currentScale = toRate
        }
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false
        val factor = 0.4f
        val set = AnimatorSet()
        if (velocityX != 0) {
            val dx = factor * velocityX / 2
            ValueAnimator.ofFloat(x, getPositionX(x + dx)).apply {
                addUpdateListener { x = getPositionX(it.animatedValue as Float) }
                set.play(this)
            }
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val dy = factor * velocityY / 2
            ValueAnimator.ofFloat(y, getPositionY(y + dy)).apply {
                addUpdateListener { y = getPositionY(it.animatedValue as Float) }
                set.play(this)
            }
        }
        set.duration = 400
        set.interpolator = DecelerateInterpolator()
        set.start()
        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) x = getPositionX(x + dx)
        if (dy != 0) y = getPositionY(y + dy)
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
        x = getPositionX(x)
    }

    fun onScale(scaleFactor: Float) {
        currentScale = (currentScale * scaleFactor).coerceIn(minRate, MAX_SCALE_RATE)
        setScaleRate(currentScale)
        if (currentScale < 1) {
            layoutParams.height = (originalHeight / currentScale).toInt()
            halfHeight = layoutParams.height / 2
        } else {
            layoutParams.height = MATCH_PARENT
            halfHeight = originalHeight / 2
        }
        if (currentScale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }
        requestLayout()
    }

    fun onScaleBegin() {
        if (detector.isDoubleTapping) detector.isQuickScaling = true
    }

    fun onScaleEnd() {
        if (scaleX < minRate) zoom(currentScale, minRate, x, 0f, y, 0f)
    }

    fun onManualScroll() {
        isManuallyScrolling = true
    }

    inner class GestureListener : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            if (!tapDuringManualScroll && isAttachedToWindow) tapListener?.invoke(ev)
            return false
        }

        override fun onDoubleTap(ev: MotionEvent): Boolean {
            detector.isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(ev: MotionEvent) {
            if (isZooming || !doubleTapZoom) return
            if (scaleX != DEFAULT_RATE) {
                zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                layoutParams.height = MATCH_PARENT
                halfHeight = originalHeight / 2
                requestLayout()
            } else {
                val toScale = 2f
                val toX = (halfWidth - ev.x) * (toScale - 1)
                val toY = (halfHeight - ev.y) * (toScale - 1)
                zoom(DEFAULT_RATE, toScale, 0f, toX, 0f, toY)
            }
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (longTapListener?.invoke(ev) == true) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    inner class Detector : GestureDetectorWithLongTap(context, listener) {
        private var scrollPointerId = 0
        private var downX = 0
        private var downY = 0
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var isZoomDragging = false
        var isDoubleTapping = false
        var isQuickScaling = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked
            val actionIndex = ev.actionIndex
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollPointerId = ev.getPointerId(0)
                    downX = (ev.x + 0.5f).toInt()
                    downY = (ev.y + 0.5f).toInt()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollPointerId = ev.getPointerId(actionIndex)
                    downX = (ev.getX(actionIndex) + 0.5f).toInt()
                    downY = (ev.getY(actionIndex) + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDoubleTapping && isQuickScaling) return true
                    val index = ev.findPointerIndex(scrollPointerId)
                    if (index < 0) return false
                    var dx = (ev.getX(index) + 0.5f).toInt() - downX
                    var dy = if (atFirstPosition || atLastPosition) {
                        (ev.getY(index) + 0.5f).toInt() - downY
                    } else {
                        0
                    }
                    if (!isZoomDragging && currentScale > 1f) {
                        var startScroll = false
                        if (abs(dx) > touchSlop) {
                            dx += if (dx < 0) touchSlop else -touchSlop
                            startScroll = true
                        }
                        if (abs(dy) > touchSlop) {
                            dy += if (dy < 0) touchSlop else -touchSlop
                            startScroll = true
                        }
                        if (startScroll) isZoomDragging = true
                    }
                    if (isZoomDragging) zoomScrollBy(dx, dy)
                }
                MotionEvent.ACTION_UP -> {
                    if (isDoubleTapping && !isQuickScaling) listener.onDoubleTapConfirmed(ev)
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isZoomDragging = false
                    isDoubleTapping = false
                    isQuickScaling = false
                }
            }
            return super.onTouchEvent(ev)
        }
    }
}

/** Ported from Mihon's `viewer/webtoon/WebtoonFrame.kt`. */
class WebtoonFrame(context: Context) : FrameLayout(context) {
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val flingDetector = GestureDetector(context, FlingListener())

    var doubleTapZoom = true
        set(value) {
            field = value
            recycler?.doubleTapZoom = value
            scaleDetector.isQuickScaleEnabled = value
        }

    var zoomOutDisabled = false
        set(value) {
            field = value
            recycler?.zoomOutDisabled = value
        }

    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)
        val recyclerRect = Rect()
        recycler?.getHitRect(recyclerRect) ?: return super.dispatchTouchEvent(ev)
        recyclerRect.inset(1, 1)
        if (recyclerRect.right < recyclerRect.left || recyclerRect.bottom < recyclerRect.top) {
            return super.dispatchTouchEvent(ev)
        }
        ev.setLocation(
            ev.x.coerceIn(recyclerRect.left.toFloat(), recyclerRect.right.toFloat()),
            ev.y.coerceIn(recyclerRect.top.toFloat(), recyclerRect.bottom.toFloat()),
        )
        return super.dispatchTouchEvent(ev)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            recycler?.onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd()
        }
    }

    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            recycler?.onManualScroll()
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }
}

private const val ANIMATOR_DURATION_TIME = 200
private const val MIN_RATE = 0.5f
private const val DEFAULT_RATE = 1f
private const val MAX_SCALE_RATE = 3f
