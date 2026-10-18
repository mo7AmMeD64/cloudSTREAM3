package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/**
 * The layout Globals from the original app. KodaStream ships only the phone
 * and tablet layouts, so the layout is always PHONE here; isLayout(TV) and
 * isLayout(EMULATOR) answer false, which is what the ported player expects
 * on a handheld device.
 */
object Globals {
    var beneneCount = 0

    const val PHONE: Int = 0b00001
    const val TV: Int = 0b00010
    const val EMULATOR: Int = 0b00100

    private var layoutId = PHONE

    fun Context.updateTv() {
        layoutId = PHONE
    }

    /** Returns true if the current orientation is landscape. */
    fun isLandscape(): Boolean =
        Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Valid flags are: PHONE, TV, EMULATOR */
    fun isLayout(flags: Int): Boolean {
        return (layoutId and flags) != 0
    }
}
