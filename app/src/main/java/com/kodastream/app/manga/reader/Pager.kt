package com.kodastream.app.manga.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Ported from Mihon's `viewer/GestureDetectorWithLongTap.kt`; the built-in
 * long press conflicts with quick-scale, so it is timed separately.
 */
open class GestureDetectorWithLongTap(
    context: Context,
    listener: Listener,
) : GestureDetector(context, listener) {

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longTapTime = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTime = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var lastUp = 0L
    private var lastDownEvent: MotionEvent? = null

    private val longTapFn = Runnable { lastDownEvent?.let(listener::onLongTapConfirmed) }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownEvent?.recycle()
                lastDownEvent = MotionEvent.obtain(ev)
                if (ev.downTime - lastUp > doubleTapTime) {
                    downX = ev.x
                    downY = ev.y
                    handler.postDelayed(longTapFn, longTapTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) {
                    handler.removeCallbacks(longTapFn)
                }
            }
            MotionEvent.ACTION_UP -> {
                lastUp = ev.eventTime
                handler.removeCallbacks(longTapFn)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacks(longTapFn)
            }
        }
        return super.onTouchEvent(ev)
    }

    open class Listener : SimpleOnGestureListener() {
        open fun onLongTapConfirmed(ev: MotionEvent) {}
    }
}

/**
 * Ported from Mihon's `viewer/pager/Pager.kt`: a directional pager with tap
 * and long-tap callbacks and the same crash guards around child gestures.
 */
open class Pager(
    context: Context,
    isHorizontal: Boolean = true,
) : androidx.viewpager.widget.DirectionalViewPager(context, isHorizontal) {

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private val gestureDetector = GestureDetectorWithLongTap(context, gestureListener)
    private var isGestureDetectorEnabled = true

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (isGestureDetectorEnabled) gestureDetector.onTouchEvent(ev)
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = try {
        super.onInterceptTouchEvent(ev)
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = try {
        super.onTouchEvent(ev)
    } catch (_: NullPointerException) {
        false
    } catch (_: IndexOutOfBoundsException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun executeKeyEvent(event: android.view.KeyEvent): Boolean = false

    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }
}
