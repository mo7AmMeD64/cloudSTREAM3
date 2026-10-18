package com.lagradost.cloudstream3

import android.app.Activity
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import kotlin.math.min
import java.lang.ref.WeakReference

/**
 * The part of CloudStream's CommonActivity that extensions actually call:
 * the current activity and toast helpers. Everything else in the original is
 * View-based UI and has no place in a Compose app.
 */
object CommonActivity {

    private var _activity: WeakReference<Activity>? = null
    var activity: Activity?
        get() = _activity?.get()
        private set(value) {
            _activity = WeakReference(value)
        }

    @MainThread
    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    // ---- members the ported CloudStream player relies on ----

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight always refer to the screen
    // while in landscape mode.
    val screenWidth: Int
        get() = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

    val screenHeight: Int
        get() = min(displayMetrics.widthPixels, displayMetrics.heightPixels)

    val screenWidthWithOrientation: Int
        get() = displayMetrics.widthPixels

    val screenHeightWithOrientation: Int
        get() = displayMetrics.heightPixels

    var isPipDesired: Boolean = false
    var isInPIPMode: Boolean = false

    /** The player registers a global key handler (TV remotes, dials). */
    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null

    private var currentToast: Toast? = null

    fun showToast(message: String?, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, message, duration)
        }
    }

    fun showToast(@StringRes message: Int, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, act.getString(message), duration)
        }
    }

    /** duration is Toast.LENGTH_SHORT if null */
    @MainThread
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (act == null || message == null) return
        try {
            currentToast?.cancel()
        } catch (_: Throwable) {
        }
        try {
            val toast = Toast.makeText(act, message.trim(), duration ?: Toast.LENGTH_SHORT)
            currentToast = toast
            toast.show()
        } catch (_: Throwable) {
        }
    }
}
