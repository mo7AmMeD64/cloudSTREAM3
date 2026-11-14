package com.kodastream.app.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.ProgressBar
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.Slider
import com.kodastream.app.R
import com.kodastream.app.ui.theme.ThemeViewModel

/**
 * The player is still XML, so it cannot read the Compose palette. [resolve]
 * caches the active palette's accent before the fragment inflates; every
 * helper falls back to white when it was never called.
 */
object PlayerAccent {
    private var scheme: ColorScheme? = null

    val color: Int get() = scheme?.primary?.toArgb() ?: 0xFFFFFFFF.toInt()
    val onColor: Int get() = scheme?.onPrimary?.toArgb() ?: 0xFF0B0B0F.toInt()

    fun resolve(context: Context) {
        scheme = ThemeViewModel.playerColorScheme(context)
    }

    /** Filled primary action, used by the dialogs' Apply buttons. */
    fun fill(button: MaterialButton?) {
        button ?: return
        button.backgroundTintList = ColorStateList.valueOf(color)
        button.setTextColor(onColor)
        button.iconTint = ColorStateList.valueOf(onColor)
        button.strokeColor = ColorStateList.valueOf(color)
    }

    /** Segmented choice: selected pill uses the accent, the rest stay chrome. */
    fun toggle(button: MaterialButton?, active: Boolean) {
        button ?: return
        val ctx = button.context
        val text = ColorStateList.valueOf(
            if (active) onColor else ContextCompat.getColor(ctx, R.color.textColor)
        )
        button.backgroundTintList = ColorStateList.valueOf(
            if (active) color else ContextCompat.getColor(ctx, R.color.player_control_surface)
        )
        button.setTextColor(text)
        button.iconTint = text
        button.strokeColor = ColorStateList.valueOf(
            if (active) color else ContextCompat.getColor(ctx, R.color.player_control_outline)
        )
    }

    fun slider(slider: Slider?) {
        slider ?: return
        slider.trackActiveTintList = ColorStateList.valueOf(color)
        slider.trackInactiveTintList =
            ColorStateList.valueOf(ContextCompat.getColor(slider.context, R.color.player_control_outline))
        slider.thumbTintList = ColorStateList.valueOf(color)
        slider.haloTintList = ColorStateList.valueOf((color and 0x00FFFFFF) or 0x33000000)
    }

    fun progress(indicator: CircularProgressIndicator?) {
        indicator?.setIndicatorColor(color)
    }

    /** Volume / brightness bars draw from a layer-list; tint only the progress layer. */
    fun levelBar(bar: ProgressBar?) {
        bar ?: return
        val drawable = bar.progressDrawable?.mutate() as? LayerDrawable ?: return
        drawable.findDrawableByLayerId(android.R.id.progress)?.setTint(color)
        bar.progressDrawable = drawable
    }
}

/** Material 3 expressive easing, shared by the player's View animations. */
object PlayerMotion {
    val emphasized: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val emphasizedDecel: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    const val MEDIUM = 300L
    const val SHORT = 160L
}
