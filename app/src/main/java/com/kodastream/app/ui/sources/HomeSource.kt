package com.kodastream.app.ui.sources

/**
 * Picks the provider Home should show. [saved] is the user's persisted choice,
 * [current] the name on screen, and [available] the home-capable providers
 * registered right now. A missing saved provider is not replaced until startup
 * has finished, so a slow extension cannot redirect Home to another source.
 *
 * Pure and one-directional: nothing here persists a value, and a fallback is
 * never treated as a choice.
 */
internal fun resolveHomeSource(
    saved: String?,
    current: String?,
    userPicked: Boolean,
    available: List<String>,
    startupDone: Boolean,
): String? {
    if (userPicked && current != null && current in available) return current
    if (saved != null && saved in available) return saved
    if (current != null && current in available) return current
    if (!startupDone) return null
    return available.firstOrNull()
}
