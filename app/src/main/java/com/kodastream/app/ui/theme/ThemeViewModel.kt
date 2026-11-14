package com.kodastream.app.ui.theme

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel

/** Holds the look the user picked and remembers it. */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var mode by mutableStateOf(readMode())
        private set
    var palette by mutableStateOf(prefs.getString(KEY_PALETTE, DYNAMIC_PALETTE_ID) ?: DYNAMIC_PALETTE_ID)
        private set
    var amoled by mutableStateOf(prefs.getBoolean(KEY_AMOLED, false))
        private set
    var style by mutableStateOf(PaletteStyle.fromStorageId(prefs.getString(KEY_STYLE, null)))
        private set

    private fun readMode(): ThemeMode {
        val saved = prefs.getString(KEY_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.DARK
    }

    fun updateMode(value: ThemeMode) {
        mode = value
        prefs.edit { putString(KEY_MODE, value.name) }
        AppCompatDelegate.setDefaultNightMode(value.nightMode)
    }

    fun updatePalette(id: String) {
        palette = id
        prefs.edit { putString(KEY_PALETTE, id) }
    }

    fun updateAmoled(value: Boolean) {
        amoled = value
        prefs.edit { putBoolean(KEY_AMOLED, value) }
    }

    fun updateStyle(value: PaletteStyle) {
        style = value
        prefs.edit { putString(KEY_STYLE, value.storageId) }
    }

    companion object {
        private const val PREFS = "kodastream_theme"
        private const val KEY_MODE = "mode"
        private const val KEY_PALETTE = "palette"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_STYLE = "style"

        /**
         * Matches the window theme to the saved in-app mode. Must run before
         * `super.onCreate`, because AppCompat reads the mode there.
         */
        fun applyNightMode(context: Context) {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, null)
            val mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.DARK
            AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        }
    }
}

private val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
