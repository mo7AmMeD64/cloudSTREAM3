package com.kodastream.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.kodastream.app.R

/** The launcher icons the Appearance setting can switch between. */
object AppIcons {
    data class Option(
        val id: String,
        @DrawableRes val previewRes: Int,
        /** The activity-alias in the manifest that carries this icon. */
        val alias: String,
        /**
         * The adaptive foreground keeps a third of its canvas as safe-zone
         * margin, so the picker scales that margin away to fill the tile.
         */
        val previewScale: Float = 1f,
    )

    const val DEFAULT = "default"

    val options = listOf(
        Option(DEFAULT, R.drawable.ic_launcher_foreground, ".LauncherDefault"),
        Option("1", R.drawable.ic_launcher_fg_1, ".LauncherIcon1", 1.5f),
        Option("2", R.drawable.ic_launcher_fg_2, ".LauncherIcon2", 1.5f),
        Option("3", R.drawable.ic_launcher_fg_3, ".LauncherIcon3", 1.5f),
        Option("4", R.drawable.ic_launcher_fg_4, ".LauncherIcon4", 1.5f),
        Option("5", R.drawable.ic_launcher_fg_5, ".LauncherIcon5", 1.5f),
    )

    /**
     * Enables the chosen alias before disabling the others, so the launcher
     * never sees a moment with no entry and drops the shortcut. DONT_KILL_APP
     * keeps the running screen alive; some launchers refresh on their next
     * draw, not instantly.
     */
    fun apply(context: Context, id: String) {
        val chosen = options.firstOrNull { it.id == id } ?: return
        setEnabled(context, chosen.alias, true)
        options.forEach { option ->
            if (option.id != id) setEnabled(context, option.alias, false)
        }
    }

    private fun setEnabled(context: Context, alias: String, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, context.packageName + alias),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
