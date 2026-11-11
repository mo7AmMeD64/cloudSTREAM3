package com.kodastream.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.kodastream.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app settings that are not part of the theme. */
object AppPrefs {
    private const val PREFS = "kodastream_prefs"
    private const val KEY_PROVIDER = "selected_provider"
    private const val KEY_AUTO_UPDATE = "auto_update_extensions"
    private const val KEY_NAV_POSITION = "nav_position"
    private const val KEY_NAV_BAR_STYLE = "nav_bar_style"
    private const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
    private const val KEY_NAV_BAR_ICON_SIZE = "nav_bar_icon_size"
    private const val KEY_NAV_BAR_WIDTH = "nav_bar_width"
    private const val KEY_NAV_BAR_HEIGHT = "nav_bar_height"
    private const val KEY_APP_MODE = "app_mode"
    private const val KEY_HOME_HERO_STYLE = "home_hero_style"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_PAUSE_HISTORY = "pause_history"
    private const val KEY_WATCH_STATISTICS_ENABLED = "watch_statistics_enabled"
    private const val KEY_PLAYER_PROGRESS_STYLE = "player_progress_style"
    private const val KEY_PLAYER_SHOW_SOURCE = "player_show_source_control"
    private const val KEY_PLAYER_SHOW_LOCK = "player_show_lock_control"
    private const val KEY_PLAYER_SHOW_ROTATE = "player_show_rotate_control"
    private const val KEY_PLAYER_SHOW_RESIZE = "player_show_resize_control"
    private const val KEY_PLAYER_SHOW_SUBTITLE_OFFSET = "player_show_subtitle_offset_control"
    private const val KEY_PLAYER_SHOW_SEEK_BUTTONS = "player_show_seek_buttons"
    private const val KEY_PLAYER_COMPACT_CONTROLS = "player_compact_controls"
    private const val KEY_DL_TREE = "download_tree_uri"
    private const val KEY_DL_WIFI = "download_wifi_only"
    private const val KEY_DL_MAX = "download_max_concurrent"
    private const val KEY_DL_SERVER = "download_last_server"
    private const val KEY_DL_FOLDERS_DONE = "download_folders_migrated"
    private const val KEY_EXTERNAL_MANAGER = "external_download_manager"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_POSTER_SIZE = "poster_size_step"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_UPDATE_CHECK = "update_last_check"
    private const val KEY_UPDATE_SKIPPED = "update_skipped_version"
    private const val KEY_UPDATE_VERSION = "update_version"
    private const val KEY_UPDATE_URL = "update_url"
    private const val KEY_UPDATE_NOTES = "update_notes"
    private const val KEY_UPDATE_ETAG = "update_etag"
    private const val POSITION_PREFIX = "pos_"
    private const val DURATION_PREFIX = "dur_"

    const val NAV_SIDE = "side"
    const val NAV_BOTTOM = "bottom"
    const val NAV_BAR_CLOUD = "cloud"
    const val NAV_BAR_NUVIO = "nuvio"
    const val LANGUAGE_SYSTEM = "system"

    private val _navPosition = MutableStateFlow<String?>(null)

    /** The bar position, as a flow so the shell can switch layouts the moment it changes. */
    val navPosition: StateFlow<String?> = _navPosition.asStateFlow()

    /** Where the navigation bar sits. Side (left) is the default. */
    fun navPosition(context: Context): String =
        _navPosition.value
            ?: prefs(context).getString(KEY_NAV_POSITION, null)?.also { _navPosition.value = it }
            ?: NAV_SIDE

    fun setNavPosition(context: Context, value: String) {
        _navPosition.value = value
        prefs(context).edit { putString(KEY_NAV_POSITION, value) }
    }

    private val _navBarStyle = MutableStateFlow<String?>(null)

    /** The bottom bar style, as a flow so the shell swaps it the moment it changes. */
    val navBarStyle: StateFlow<String?> = _navBarStyle.asStateFlow()

    /** "cloud" (the Material floating pill) or "nuvio" (the jelly bar). */
    fun navBarStyle(context: Context): String =
        _navBarStyle.value
            ?: prefs(context).getString(KEY_NAV_BAR_STYLE, null)?.also { _navBarStyle.value = it }
            ?: NAV_BAR_CLOUD

    fun setNavBarStyle(context: Context, value: String) {
        _navBarStyle.value = value
        prefs(context).edit { putString(KEY_NAV_BAR_STYLE, value) }
    }

    /** Nuvio bar tuning: icon dp (20-34), bar width dp (240-480), expanded height dp (48-84). */
    fun navBarIconSize(context: Context): Int =
        prefs(context).getInt(KEY_NAV_BAR_ICON_SIZE, 28)

    fun setNavBarIconSize(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_NAV_BAR_ICON_SIZE, value) }
    }

    fun navBarWidth(context: Context): Int =
        prefs(context).getInt(KEY_NAV_BAR_WIDTH, 400)

    fun setNavBarWidth(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_NAV_BAR_WIDTH, value) }
    }

    fun navBarHeight(context: Context): Int =
        prefs(context).getInt(KEY_NAV_BAR_HEIGHT, 64)

    fun setNavBarHeight(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_NAV_BAR_HEIGHT, value) }
    }

    private val _hideStatusBar = MutableStateFlow<Boolean?>(null)

    /** Hiding the system status bar, as a flow so the window applies it immediately. */
    val hideStatusBar: StateFlow<Boolean?> = _hideStatusBar.asStateFlow()

    fun hideStatusBar(context: Context): Boolean =
        _hideStatusBar.value
            ?: prefs(context).getBoolean(KEY_HIDE_STATUS_BAR, false).also { _hideStatusBar.value = it }

    fun setHideStatusBar(context: Context, value: Boolean) {
        _hideStatusBar.value = value
        prefs(context).edit { putBoolean(KEY_HIDE_STATUS_BAR, value) }
    }

    /** "carousel", "cinematic" or "off"; unknown values are resolved by the caller. */
    fun homeHeroStyle(context: Context): String =
        prefs(context).getString(KEY_HOME_HERO_STYLE, null) ?: "carousel"

    fun setHomeHeroStyle(context: Context, value: String) {
        prefs(context).edit { putString(KEY_HOME_HERO_STYLE, value) }
    }

    /** The player's chosen speed, remembered between episodes. */
    fun playbackSpeed(context: Context): Float = prefs(context).getFloat(KEY_SPEED, 1f)

    fun setPlaybackSpeed(context: Context, value: Float) {
        prefs(context).edit { putFloat(KEY_SPEED, value) }
    }

    /**
     * Hold-to-speed-up lives in the port's default preferences, the file its
     * gesture helper reads. [ensureHoldToSpeedUp] seeds the default once, so a
     * later "off" choice is not overwritten.
     */
    fun holdToSpeedUp(context: Context): Boolean =
        playerPrefs(context).getBoolean(context.getString(R.string.speedup_key), true)

    fun setHoldToSpeedUp(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(context.getString(R.string.speedup_key), value) }
    }

    fun ensureHoldToSpeedUp(context: Context) {
        val prefs = playerPrefs(context)
        val key = context.getString(R.string.speedup_key)
        if (!prefs.contains(key)) prefs.edit { putBoolean(key, true) }
    }

    private fun playerPrefs(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)

    // ---- Player gestures and controls ----
    //
    // These live in the ported player's own preference file, so the gesture helper and
    // the player view read the very same values on the next playback. Every default
    // here equals the default the player itself falls back to, so a user who never
    // opens the Player settings page sees no change in behavior.

    /** Vertical swipe: brightness on the left half, volume on the right half. */
    fun playerVerticalSwipeEnabled(context: Context): Boolean =
        playerPrefs(context).getBoolean(context.getString(R.string.swipe_vertical_enabled_key), true)

    fun setPlayerVerticalSwipeEnabled(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(context.getString(R.string.swipe_vertical_enabled_key), value) }
    }

    /** Horizontal swipe: seek through the video. */
    fun playerHorizontalSwipeEnabled(context: Context): Boolean =
        playerPrefs(context).getBoolean(context.getString(R.string.swipe_enabled_key), true)

    fun setPlayerHorizontalSwipeEnabled(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(context.getString(R.string.swipe_enabled_key), value) }
    }

    /** Double-tap the left or right side to seek back or forward. */
    fun playerDoubleTapEnabled(context: Context): Boolean =
        playerPrefs(context).getBoolean(context.getString(R.string.double_tap_enabled_key), false)

    fun setPlayerDoubleTapEnabled(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(context.getString(R.string.double_tap_enabled_key), value) }
    }

    /** Double-tap the middle to pause or resume. */
    fun playerDoubleTapPauseEnabled(context: Context): Boolean =
        playerPrefs(context).getBoolean(context.getString(R.string.double_tap_pause_enabled_key), false)

    fun setPlayerDoubleTapPauseEnabled(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(context.getString(R.string.double_tap_pause_enabled_key), value) }
    }

    /** Seconds one double-tap seek moves; one of [PLAYER_SEEK_SECONDS_OPTIONS]. */
    fun playerSeekSeconds(context: Context): Int =
        playerPrefs(context).getInt(context.getString(R.string.double_tap_seek_time_key), 10)
            .coerceIn(PLAYER_SEEK_SECONDS_MIN, PLAYER_SEEK_SECONDS_MAX)

    fun setPlayerSeekSeconds(context: Context, seconds: Int) {
        playerPrefs(context).edit {
            putInt(
                context.getString(R.string.double_tap_seek_time_key),
                seconds.coerceIn(PLAYER_SEEK_SECONDS_MIN, PLAYER_SEEK_SECONDS_MAX),
            )
        }
    }

    const val PLAYER_SEEK_SECONDS_MIN = 5
    const val PLAYER_SEEK_SECONDS_MAX = 20
    val PLAYER_SEEK_SECONDS_OPTIONS = listOf(5, 10, 15, 20)

    const val PLAYER_PROGRESS_SLIM = "slim"
    const val PLAYER_PROGRESS_STANDARD = "standard"
    const val PLAYER_PROGRESS_BOLD = "bold"

    /** Thickness of the progress bar: slim, standard (default) or bold. */
    fun playerProgressStyle(context: Context): String =
        playerPrefs(context).getString(KEY_PLAYER_PROGRESS_STYLE, PLAYER_PROGRESS_STANDARD)
            ?.takeIf { it == PLAYER_PROGRESS_SLIM || it == PLAYER_PROGRESS_BOLD }
            ?: PLAYER_PROGRESS_STANDARD

    fun setPlayerProgressStyle(context: Context, value: String) {
        playerPrefs(context).edit { putString(KEY_PLAYER_PROGRESS_STYLE, value) }
    }

    // Control visibility. All are on by default and are read when a playback screen opens.
    fun playerShowSourceControl(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_SOURCE, true)

    fun setPlayerShowSourceControl(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_SOURCE, value) }
    }

    fun playerShowLockControl(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_LOCK, true)

    fun setPlayerShowLockControl(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_LOCK, value) }
    }

    fun playerShowRotateControl(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_ROTATE, true)

    fun setPlayerShowRotateControl(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_ROTATE, value) }
    }

    fun playerShowResizeControl(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_RESIZE, true)

    fun setPlayerShowResizeControl(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_RESIZE, value) }
    }

    fun playerShowSubtitleOffsetControl(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_SUBTITLE_OFFSET, true)

    fun setPlayerShowSubtitleOffsetControl(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_SUBTITLE_OFFSET, value) }
    }

    fun playerShowSeekButtons(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, true)

    fun setPlayerShowSeekButtons(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, value) }
    }

    /** Smaller control icons with the same touch targets. On by default. */
    fun playerCompactControls(context: Context): Boolean =
        playerPrefs(context).getBoolean(KEY_PLAYER_COMPACT_CONTROLS, true)

    fun setPlayerCompactControls(context: Context, value: Boolean) {
        playerPrefs(context).edit { putBoolean(KEY_PLAYER_COMPACT_CONTROLS, value) }
    }

    // ---- Watch statistics ----

    /** Counting viewing time. Turning it off stops counting; stored numbers stay. */
    fun watchStatisticsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WATCH_STATISTICS_ENABLED, true)

    fun setWatchStatisticsEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_WATCH_STATISTICS_ENABLED, value) }
    }

    /** True when nothing new should be written to the watch history. Existing entries stay. */
    fun pauseHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_HISTORY, false)

    fun setPauseHistory(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_PAUSE_HISTORY, value) }
    }

    // ---- Downloads ----

    /** The SAF tree the user picked, or null for the app's default folder. */
    fun downloadTreeUri(context: Context): String? = prefs(context).getString(KEY_DL_TREE, null)

    fun setDownloadTreeUri(context: Context, uri: String?) {
        prefs(context).edit { if (uri == null) remove(KEY_DL_TREE) else putString(KEY_DL_TREE, uri) }
    }

    fun downloadWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_DL_WIFI, false)

    fun setDownloadWifiOnly(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_DL_WIFI, value) }
    }

    fun downloadMaxConcurrent(context: Context): Int = prefs(context).getInt(KEY_DL_MAX, 2).coerceIn(1, 3)

    fun setDownloadMaxConcurrent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_DL_MAX, value.coerceIn(1, 3)) }
    }

    /** True once the one-time move into per-title folders has run. */
    fun downloadsFolderMigrated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DL_FOLDERS_DONE, false)

    fun setDownloadsFolderMigrated(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_DL_FOLDERS_DONE, value) }
    }

    /** Keep the screen awake while the player plays. On by default. */
    fun keepScreenOn(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOn(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }
    }

    /** The server name last picked in the download card, preselected next time. */
    fun lastDownloadServer(context: Context): String? = prefs(context).getString(KEY_DL_SERVER, null)

    fun setLastDownloadServer(context: Context, value: String) {
        prefs(context).edit { putString(KEY_DL_SERVER, value) }
    }

    /** The poster size step shared by History and the Library grids. */
    fun posterSizeStep(context: Context): Int =
        posterStepOrFallback(prefs(context).getInt(KEY_POSTER_SIZE, POSTER_SIZE_DEFAULT_STEP))

    fun setPosterSizeStep(context: Context, step: Int) {
        prefs(context).edit { putInt(KEY_POSTER_SIZE, posterStepOrFallback(step)) }
    }

    /** Package name of the external download manager used last, if any. */
    fun externalDownloadManager(context: Context): String? =
        prefs(context).getString(KEY_EXTERNAL_MANAGER, null)

    fun setExternalDownloadManager(context: Context, packageName: String?) {
        prefs(context).edit { putString(KEY_EXTERNAL_MANAGER, packageName) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedProvider(context: Context): String? = prefs(context).getString(KEY_PROVIDER, null)

    const val MODE_WATCH = "watch"
    const val MODE_MANGA = "manga"

    /** Watch or manga; the navigation remembers which one was open. */
    fun appMode(context: Context): String =
        prefs(context).getString(KEY_APP_MODE, MODE_WATCH)
            .takeIf { it == MODE_MANGA } ?: MODE_WATCH

    fun setAppMode(context: Context, mode: String) {
        prefs(context).edit { putString(KEY_APP_MODE, mode) }
    }

    fun setSelectedProvider(context: Context, name: String?) {
        prefs(context).edit { if (name == null) remove(KEY_PROVIDER) else putString(KEY_PROVIDER, name) }
    }

    fun autoUpdate(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdate(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_UPDATE, value) }
    }

    /** Where playback stopped, in milliseconds. 0 when unknown. */
    fun position(context: Context, key: String): Long =
        prefs(context).getLong(POSITION_PREFIX + key.hashCode(), 0L)

    fun setPosition(context: Context, key: String, positionMs: Long) {
        prefs(context).edit { putLong(POSITION_PREFIX + key.hashCode(), positionMs) }
    }

    /** The last known length of one episode, in milliseconds. 0 when unknown. */
    fun duration(context: Context, key: String): Long =
        prefs(context).getLong(DURATION_PREFIX + key.hashCode(), 0L)

    fun setDuration(context: Context, key: String, durationMs: Long) {
        prefs(context).edit { putLong(DURATION_PREFIX + key.hashCode(), durationMs) }
    }

    // ---- First-run setup ----

    fun setupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupDone(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SETUP_DONE, value) }
    }

    /** "system" or a BCP 47 language tag such as "en" / "ar". */
    fun appLanguage(context: Context): String =
        prefs(context).getString(KEY_APP_LANGUAGE, null) ?: LANGUAGE_SYSTEM

    fun setAppLanguage(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_APP_LANGUAGE, tag) }
    }

    /** Applies the saved language; called from Application before any activity exists. */
    fun applyAppLanguage(context: Context) {
        val locales = appLanguage(context).takeIf { it != LANGUAGE_SYSTEM }
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    // ---- App updates ----

    /** When the GitHub release check last completed, in epoch millis. */
    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong(KEY_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(context: Context, timeMs: Long) {
        prefs(context).edit { putLong(KEY_UPDATE_CHECK, timeMs) }
    }

    /** The version the user chose to stop being reminded about. */
    fun skippedUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_UPDATE_SKIPPED, null)

    fun setSkippedUpdateVersion(context: Context, version: String?) {
        prefs(context).edit {
            if (version == null) remove(KEY_UPDATE_SKIPPED) else putString(KEY_UPDATE_SKIPPED, version)
        }
    }

    /** The last official release found, offered again without a network call. */
    fun storedUpdate(context: Context): AppUpdate? {
        val prefs = prefs(context)
        val version = prefs.getString(KEY_UPDATE_VERSION, null) ?: return null
        val url = prefs.getString(KEY_UPDATE_URL, null) ?: return null
        return AppUpdate(version, prefs.getString(KEY_UPDATE_NOTES, null), url)
    }

    fun setStoredUpdate(context: Context, update: AppUpdate?) {
        prefs(context).edit {
            if (update == null) {
                remove(KEY_UPDATE_VERSION)
                remove(KEY_UPDATE_URL)
                remove(KEY_UPDATE_NOTES)
            } else {
                putString(KEY_UPDATE_VERSION, update.version)
                putString(KEY_UPDATE_URL, update.url)
                putString(KEY_UPDATE_NOTES, update.notes)
            }
        }
    }

    /** The GitHub ETag of the last successful release response. */
    fun updateEtag(context: Context): String? = prefs(context).getString(KEY_UPDATE_ETAG, null)

    fun setUpdateEtag(context: Context, etag: String?) {
        prefs(context).edit {
            if (etag == null) remove(KEY_UPDATE_ETAG) else putString(KEY_UPDATE_ETAG, etag)
        }
    }
}
