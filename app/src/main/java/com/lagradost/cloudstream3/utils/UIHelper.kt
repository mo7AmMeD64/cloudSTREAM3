package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.Window
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.disableBackPressedCallback

/**
 * The UI helpers the ported CloudStream player uses, brought over from the
 * original app's UIHelper one by one: system-bar padding for the XML
 * dialogs, immersive mode, safe dialog dismissal, px conversion and
 * theme-attribute colors. Settings and layout extras stayed behind.
 */
@Suppress("unused")
object UIHelper {

    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)

    fun View.isRtl(): Boolean = this.layoutDirection == View.LAYOUT_DIRECTION_RTL

    @ColorInt
    fun Context.colorFromAttribute(@AttrRes attribute: Int): Int {
        var color = 0
        val typedValue = TypedValue()
        if (theme.resolveAttribute(attribute, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                color = androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
            } else {
                color = typedValue.data
            }
        }
        return color
    }

    fun Dialog?.dismissSafe(activity: Activity?) {
        if (this?.isShowing == true && activity?.isFinishing == false) {
            this.dismiss()
        }
    }

    fun Dialog?.dismissSafe() {
        dismissSafe(CommonActivity.activity)
    }

    /**
     * Applies system-bar (and cutout) padding to a view. The cutout-drawing
     * overlay from the original is left out; only the padding is applied.
     */
    fun fixSystemBarsPadding(
        v: View,
        padTop: Boolean = true,
        padBottom: Boolean = true,
        padLeft: Boolean = true,
        padRight: Boolean = true,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (padTop) {
                val ctx = v.context ?: return
                v.updatePadding(top = ctx.getStatusBarHeight())
            }
            return
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v) { view, windowInsets ->
            val leftCheck = if (view.isRtl()) padRight else padLeft
            val rightCheck = if (view.isRtl()) padLeft else padRight

            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )

            view.updatePadding(
                left = if (leftCheck) insets.left else view.paddingLeft,
                right = if (rightCheck) insets.right else view.paddingRight,
                bottom = if (padBottom) insets.bottom else view.paddingBottom,
                top = if (padTop) insets.top else view.paddingTop,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    fun Context.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Activity.hideSystemUI() {
        // Enables regular immersive mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    fun Activity.showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            return
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /** If you want to call this from a BackPressedCallback, pass the name of the callback to temporarily disable it */
    fun androidx.fragment.app.FragmentActivity.popCurrentPage(fromBackPressedCallback: String? = null) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                if (fromBackPressedCallback != null) {
                    // FragmentActivity is a ComponentActivity, so the
                    // BackPressedCallbackHelper extension resolves on this.
                    disableBackPressedCallback(fromBackPressedCallback)
                }
                if (!supportFragmentManager.isStateSaved) {
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    mainHandler.postDelayed({
                        try {
                            if (!supportFragmentManager.isStateSaved) {
                                onBackPressedDispatcher.onBackPressed()
                            }
                        } catch (t: Throwable) {
                            logError(t)
                        }
                    }, 100)
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    /** id, string */
    @android.annotation.SuppressLint("RestrictedApi")
    fun View.popupMenuNoIconsAndNoStringRes(
        items: List<Pair<Int, String>>,
        onMenuItemClick: android.view.MenuItem.() -> Unit,
    ): android.widget.PopupMenu {
        val ctw = android.view.ContextThemeWrapper(context, com.kodastream.app.R.style.PopupMenu)
        val popup = android.widget.PopupMenu(
            ctw,
            this,
            android.view.Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0,
        )

        items.forEach { (id, string) ->
            popup.menu.add(0, id, 0, string)
        }

        (popup.menu as? androidx.appcompat.view.menu.MenuBuilder)?.setOptionalIconsVisible(true)

        popup.setOnMenuItemClickListener {
            it.onMenuItemClick()
            true
        }

        popup.show()
        return popup
    }

    fun Activity.enableEdgeToEdgeCompat() {
        // edge-to-edge is very buggy on earlier versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        WindowCompat.enableEdgeToEdge(window)
    }
}
