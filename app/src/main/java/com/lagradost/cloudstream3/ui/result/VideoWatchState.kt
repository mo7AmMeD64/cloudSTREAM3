package com.lagradost.cloudstream3.ui.result

/**
 * Future proofed way to mark episodes as watched.
 * Declared in its own file here so the trimmed DataStoreHelper (and later
 * the full result subsystem) can compile against it, same package as the
 * original's ResultFragment.kt. The full ResultEpisode class lands with
 * the result subsystem in a later port stage.
 */
enum class VideoWatchState {
    /** Default value when no key is set */
    None,
    Watched,
}
