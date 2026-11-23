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
        /** The adaptive background layer, drawn behind the preview. */
        @DrawableRes val previewBackground: Int? = null,
    )

    const val DEFAULT = "default"

    /** The icon a fresh install (or a removed choice) falls back to. */
    const val DEFAULT_ID = "1"

    val options = listOf(
        Option(DEFAULT, R.drawable.ic_launcher_foreground, ".LauncherDefault"),
        Option("1", R.drawable.ic_launcher_fg_1, ".LauncherIcon1", 1.5f, R.drawable.ic_launcher_bg_1),
        Option("2", R.drawable.ic_launcher_fg_2, ".LauncherIcon2", 1.5f, R.drawable.ic_launcher_bg_2),
        Option("3", R.drawable.ic_launcher_fg_3, ".LauncherIcon3", 1.5f, R.drawable.ic_launcher_bg_3),
    )

    /** Aliases removed from the picker; kept in the manifest so an update never loses the launcher entry. */
    private val retiredAliases = listOf(".LauncherIcon4", ".LauncherIcon5")

    /**
     * Keeps the enabled alias in sync with the saved icon. Also fills in the
     * default on a fresh install and after an update, so the launcher and the
     * picker can never disagree.
     */
    fun applyStored(context: Context) {
        val saved = AppPrefs.appIcon(context)
        val id = resolve(saved)
        if (id != saved) AppPrefs.setAppIcon(context, id)
        apply(context, id)
    }

    /** A saved id whose icon was removed maps to the default icon. */
    internal fun resolve(saved: String?): String =
        saved?.takeIf { id -> options.any { it.id == id } } ?: DEFAULT_ID

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
        retiredAliases.forEach { setEnabled(context, it, false) }
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
