package com.kodastream.app.manga

/** Paged right-to-left is the manga default; every other mode is opt-in. */
enum class MangaReadingMode {
    PAGED_RTL,
    PAGED_LTR,
    PAGED_VERTICAL,
    WEBTOON,
    WEBTOON_GAPS;

    val isPaged: Boolean
        get() = this == PAGED_RTL || this == PAGED_LTR || this == PAGED_VERTICAL

    val readingRtl: Boolean
        get() = this == PAGED_RTL
}

enum class MangaScaleType {
    FIT_SCREEN,
    FIT_WIDTH,
    FIT_HEIGHT,
    ORIGINAL,
    SMART_FIT,
}

enum class MangaZoomStart {
    AUTOMATIC,
    LEFT,
    CENTER,
    RIGHT,
}

enum class MangaReaderTheme {
    BLACK,
    WHITE,
    GRAY,
    AUTO,
}

enum class MangaReaderOrientation {
    FREE,
    PORTRAIT,
    LANDSCAPE,
}

/** Everything the reader needs at open time, copied out of [MangaPrefs]. */
data class MangaReaderSettings(
    val mode: MangaReadingMode = MangaReadingMode.PAGED_RTL,
    val tapLayout: TapZoneLayout = TapZoneLayout.RIGHT_AND_LEFT,
    val tapInvert: TapInvert = TapInvert.NONE,
    val scaleType: MangaScaleType = MangaScaleType.FIT_SCREEN,
    val zoomStart: MangaZoomStart = MangaZoomStart.AUTOMATIC,
    val cropBorders: Boolean = false,
    val pageTransitions: Boolean = true,
    val theme: MangaReaderTheme = MangaReaderTheme.BLACK,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val volumeKeys: Boolean = true,
    val longTapSave: Boolean = true,
    val webtoonPadding: Int = 0,
    val doubleTapZoom: Boolean = true,
    val orientation: MangaReaderOrientation = MangaReaderOrientation.FREE,
)
