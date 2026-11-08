package com.kodastream.app.ui.details

/**
 * Tracks independently scrolling surfaces on one details page. A parent vertical
 * list can become idle while the nested horizontal timeline is still moving; a
 * single Boolean callback lets either surface incorrectly resume provider work.
 * This tracker keeps enrichment paused until every registered surface is idle.
 */
internal class ScrollActivityTracker {
    private val activeSources = mutableSetOf<String>()

    @Synchronized
    fun setActive(source: String, active: Boolean): Boolean {
        require(source.isNotBlank()) { "Scroll source must not be blank" }
        if (active) activeSources.add(source) else activeSources.remove(source)
        return activeSources.isNotEmpty()
    }

    @Synchronized
    fun clear(): Boolean {
        activeSources.clear()
        return false
    }
}
