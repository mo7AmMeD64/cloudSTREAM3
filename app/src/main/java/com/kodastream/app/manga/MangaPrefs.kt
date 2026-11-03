package com.kodastream.app.manga

import android.content.Context
import androidx.core.content.edit

/**
 * Remembered choices for the manga section, kept apart from the video
 * preferences. Every key here is manga-only; the video side reads none of it.
 */
object MangaPrefs {
    private const val PREFS = "manga_prefs"
    private const val KEY_NSFW = "show_nsfw"
    private const val KEY_NSFW_GLOBAL = "show_nsfw_global"
    private const val KEY_ALL_LANGUAGES = "all_languages"
    private const val KEY_LANGUAGES = "languages"
    private const val KEY_SECTION = "manga_section"
    private const val KEY_SOURCE = "selected_source"
    private const val KEY_PINNED = "pinned_sources"
    private const val KEY_BROWSE_TAB = "browse_tab"
    private const val KEY_BROWSE_POSTER = "browse_poster_step"
    private const val KEY_LIBRARY_POSTER = "library_poster_step"
    private const val KEY_HISTORY_POSTER = "history_poster_step"
    private const val KEY_LIBRARY_SORT = "library_sort"
    private const val KEY_LIBRARY_DESC = "library_desc"
    private const val KEY_LIBRARY_FILTER_UNREAD = "library_filter_unread"
    private const val KEY_LIBRARY_FILTER_STARTED = "library_filter_started"
    private const val KEY_LIBRARY_FILTER_COMPLETED = "library_filter_completed"
    private const val KEY_LIBRARY_FILTER_SOURCE = "library_filter_source"
    private const val KEY_LIBRARY_FILTER_LANGUAGE = "library_filter_language"
    private const val KEY_LIBRARY_LAST_CHECK = "library_last_check"
    private const val KEY_READER_MODE = "reader_mode"
    private const val KEY_READER_TAP = "reader_tap"
    private const val KEY_READER_INVERT = "reader_invert"
    private const val KEY_READER_SCALE = "reader_scale"
    private const val KEY_READER_ZOOM = "reader_zoom"
    private const val KEY_READER_CROP = "reader_crop"
    private const val KEY_READER_TRANSITIONS = "reader_transitions"
    private const val KEY_READER_THEME = "reader_theme"
    private const val KEY_READER_KEEP_ON = "reader_keep_on"
    private const val KEY_READER_PAGE_NUMBER = "reader_page_number"
    private const val KEY_READER_VOLUME = "reader_volume"
    private const val KEY_READER_LONG_TAP = "reader_long_tap"
    private const val KEY_READER_WEBTOON_PADDING = "reader_webtoon_padding"
    private const val KEY_READER_DOUBLE_TAP = "reader_double_tap"
    private const val KEY_READER_ORIENTATION = "reader_orientation"
    private const val KEY_READER_CACHE_MB = "reader_cache_mb"
    private const val KEY_CHECK_ON_OPEN = "check_updates_on_open"
    private const val KEY_MODE_PREFIX = "reader_mode_for_"

    const val ALL_LANGUAGES = "all"
    const val DEFAULT_CACHE_MB = 200

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showNsfw(context: Context): Boolean = prefs(context).getBoolean(KEY_NSFW, false)

    fun setShowNsfw(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_NSFW, value) }
    }

    fun showNsfwGlobalSearch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NSFW_GLOBAL, false)

    fun setShowNsfwGlobalSearch(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_NSFW_GLOBAL, value) }
    }

    fun allLanguages(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALL_LANGUAGES, false)

    /** The selected language codes; the default is the app language plus English. */
    fun languages(context: Context): Set<String> {
        val saved = prefs(context).getStringSet(KEY_LANGUAGES, null)
        if (saved != null) return saved
        val appLanguage = context.resources.configuration.locales[0].language
        return setOf(appLanguage, "en").filter { it.isNotBlank() }.toSet()
    }

    fun setAllLanguages(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ALL_LANGUAGES, value) }
    }

    fun setLanguages(context: Context, languages: Set<String>) {
        prefs(context).edit { putStringSet(KEY_LANGUAGES, languages) }
    }

    /** Master switch for the manga mode; on until the owner turns it off. */
    fun mangaSectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SECTION, true)

    fun setMangaSectionEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SECTION, value) }
    }

    // ---------------------------------------------------------------- browse

    fun selectedSourceId(context: Context): Long? =
        prefs(context).getLong(KEY_SOURCE, -1L).takeIf { it >= 0L }

    fun setSelectedSourceId(context: Context, id: Long) {
        prefs(context).edit { putLong(KEY_SOURCE, id) }
    }

    fun pinnedSources(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_PINNED, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun setPinned(context: Context, id: Long, pinned: Boolean) {
        val next = pinnedSources(context).toMutableSet()
        if (pinned) next.add(id) else next.remove(id)
        prefs(context).edit { putStringSet(KEY_PINNED, next.map { it.toString() }.toSet()) }
    }

    /** 0 = popular, 1 = latest. */
    fun browseTab(context: Context): Int = prefs(context).getInt(KEY_BROWSE_TAB, 0)

    fun setBrowseTab(context: Context, tab: Int) {
        prefs(context).edit { putInt(KEY_BROWSE_TAB, tab) }
    }

    fun browsePosterStep(context: Context): Int =
        prefs(context).getInt(KEY_BROWSE_POSTER, 2)

    fun setBrowsePosterStep(context: Context, step: Int) {
        prefs(context).edit { putInt(KEY_BROWSE_POSTER, step) }
    }

    fun libraryPosterStep(context: Context): Int =
        prefs(context).getInt(KEY_LIBRARY_POSTER, 2)

    fun setLibraryPosterStep(context: Context, step: Int) {
        prefs(context).edit { putInt(KEY_LIBRARY_POSTER, step) }
    }

    fun historyPosterStep(context: Context): Int =
        prefs(context).getInt(KEY_HISTORY_POSTER, 2)

    fun setHistoryPosterStep(context: Context, step: Int) {
        prefs(context).edit { putInt(KEY_HISTORY_POSTER, step) }
    }

    fun librarySort(context: Context): MangaLibrarySort =
        MangaLibrarySort.entries.getOrElse(prefs(context).getInt(KEY_LIBRARY_SORT, 0)) {
            MangaLibrarySort.TITLE
        }

    fun setLibrarySort(context: Context, sort: MangaLibrarySort) {
        prefs(context).edit { putInt(KEY_LIBRARY_SORT, sort.ordinal) }
    }

    fun libraryDescending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LIBRARY_DESC, false)

    fun setLibraryDescending(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_LIBRARY_DESC, value) }
    }

    fun libraryFilter(context: Context): MangaLibraryFilter {
        val p = prefs(context)
        return MangaLibraryFilter(
            unreadOnly = p.getBoolean(KEY_LIBRARY_FILTER_UNREAD, false),
            startedOnly = p.getBoolean(KEY_LIBRARY_FILTER_STARTED, false),
            completedOnly = p.getBoolean(KEY_LIBRARY_FILTER_COMPLETED, false),
            sourceName = p.getString(KEY_LIBRARY_FILTER_SOURCE, null),
            language = p.getString(KEY_LIBRARY_FILTER_LANGUAGE, null),
        )
    }

    fun setLibraryFilter(context: Context, filter: MangaLibraryFilter) {
        prefs(context).edit {
            putBoolean(KEY_LIBRARY_FILTER_UNREAD, filter.unreadOnly)
            putBoolean(KEY_LIBRARY_FILTER_STARTED, filter.startedOnly)
            putBoolean(KEY_LIBRARY_FILTER_COMPLETED, filter.completedOnly)
            if (filter.sourceName == null) remove(KEY_LIBRARY_FILTER_SOURCE)
            else putString(KEY_LIBRARY_FILTER_SOURCE, filter.sourceName)
            if (filter.language == null) remove(KEY_LIBRARY_FILTER_LANGUAGE)
            else putString(KEY_LIBRARY_FILTER_LANGUAGE, filter.language)
        }
    }

    /** When the library last checked sources for new chapters. */
    fun libraryLastCheck(context: Context): Long =
        prefs(context).getLong(KEY_LIBRARY_LAST_CHECK, 0L)

    fun setLibraryLastCheck(context: Context, at: Long) {
        prefs(context).edit { putLong(KEY_LIBRARY_LAST_CHECK, at) }
    }

    // ---------------------------------------------------------------- reader

    fun readerMode(context: Context): MangaReadingMode =
        MangaReadingMode.entries.getOrElse(prefs(context).getInt(KEY_READER_MODE, 0)) {
            MangaReadingMode.PAGED_RTL
        }

    fun setReaderMode(context: Context, mode: MangaReadingMode) {
        prefs(context).edit { putInt(KEY_READER_MODE, mode.ordinal) }
    }

    /** A per-title choice wins over the default. */
    fun readerModeFor(context: Context, sourceId: Long, mangaUrl: String): MangaReadingMode {
        val saved = prefs(context).getInt(KEY_MODE_PREFIX + sourceId + "|" + mangaUrl, -1)
        return MangaReadingMode.entries.getOrNull(saved) ?: readerMode(context)
    }

    fun setReaderModeFor(context: Context, sourceId: Long, mangaUrl: String, mode: MangaReadingMode?) {
        prefs(context).edit {
            if (mode == null) remove(KEY_MODE_PREFIX + sourceId + "|" + mangaUrl)
            else putInt(KEY_MODE_PREFIX + sourceId + "|" + mangaUrl, mode.ordinal)
        }
    }

    fun readerSettings(context: Context, sourceId: Long, mangaUrl: String): MangaReaderSettings {
        val p = prefs(context)
        return MangaReaderSettings(
            mode = readerModeFor(context, sourceId, mangaUrl),
            tapLayout = TapZoneLayout.entries.getOrElse(p.getInt(KEY_READER_TAP, 0)) {
                TapZoneLayout.RIGHT_AND_LEFT
            },
            tapInvert = TapInvert.entries.getOrElse(p.getInt(KEY_READER_INVERT, 0)) { TapInvert.NONE },
            scaleType = MangaScaleType.entries.getOrElse(p.getInt(KEY_READER_SCALE, 0)) {
                MangaScaleType.FIT_SCREEN
            },
            zoomStart = MangaZoomStart.entries.getOrElse(p.getInt(KEY_READER_ZOOM, 0)) {
                MangaZoomStart.AUTOMATIC
            },
            cropBorders = p.getBoolean(KEY_READER_CROP, false),
            pageTransitions = p.getBoolean(KEY_READER_TRANSITIONS, true),
            theme = MangaReaderTheme.entries.getOrElse(p.getInt(KEY_READER_THEME, 0)) {
                MangaReaderTheme.BLACK
            },
            keepScreenOn = p.getBoolean(KEY_READER_KEEP_ON, true),
            showPageNumber = p.getBoolean(KEY_READER_PAGE_NUMBER, true),
            volumeKeys = p.getBoolean(KEY_READER_VOLUME, true),
            longTapSave = p.getBoolean(KEY_READER_LONG_TAP, true),
            webtoonPadding = p.getInt(KEY_READER_WEBTOON_PADDING, 0),
            doubleTapZoom = p.getBoolean(KEY_READER_DOUBLE_TAP, true),
            orientation = MangaReaderOrientation.entries.getOrElse(
                p.getInt(KEY_READER_ORIENTATION, 0)
            ) { MangaReaderOrientation.FREE },
        )
    }

    fun setReaderTapLayout(context: Context, value: TapZoneLayout) {
        prefs(context).edit { putInt(KEY_READER_TAP, value.ordinal) }
    }

    fun setReaderInvert(context: Context, value: TapInvert) {
        prefs(context).edit { putInt(KEY_READER_INVERT, value.ordinal) }
    }

    fun setReaderScaleType(context: Context, value: MangaScaleType) {
        prefs(context).edit { putInt(KEY_READER_SCALE, value.ordinal) }
    }

    fun setReaderZoomStart(context: Context, value: MangaZoomStart) {
        prefs(context).edit { putInt(KEY_READER_ZOOM, value.ordinal) }
    }

    fun setReaderCropBorders(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_CROP, value) }
    }

    fun setReaderPageTransitions(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_TRANSITIONS, value) }
    }

    fun setReaderTheme(context: Context, value: MangaReaderTheme) {
        prefs(context).edit { putInt(KEY_READER_THEME, value.ordinal) }
    }

    fun setReaderKeepScreenOn(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_KEEP_ON, value) }
    }

    fun setReaderShowPageNumber(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_PAGE_NUMBER, value) }
    }

    fun setReaderVolumeKeys(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_VOLUME, value) }
    }

    fun setReaderLongTapSave(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_LONG_TAP, value) }
    }

    fun setReaderWebtoonPadding(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_READER_WEBTOON_PADDING, value) }
    }

    fun setReaderDoubleTapZoom(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_READER_DOUBLE_TAP, value) }
    }

    fun setReaderOrientation(context: Context, value: MangaReaderOrientation) {
        prefs(context).edit { putInt(KEY_READER_ORIENTATION, value.ordinal) }
    }

    fun readerCacheMb(context: Context): Int =
        prefs(context).getInt(KEY_READER_CACHE_MB, DEFAULT_CACHE_MB).coerceIn(50, 4096)

    fun setReaderCacheMb(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_READER_CACHE_MB, value.coerceIn(50, 4096)) }
    }

    fun checkUpdatesOnOpen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CHECK_ON_OPEN, false)

    fun setCheckUpdatesOnOpen(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_CHECK_ON_OPEN, value) }
    }
}
