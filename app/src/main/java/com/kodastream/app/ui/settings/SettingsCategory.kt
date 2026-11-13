package com.kodastream.app.ui.settings

/**
 * The folders of the Settings screen. The root page lists them in [entries]
 * order; each one opens its own page that holds only its settings.
 *
 * This type is deliberately free of Android and Compose types so the route ids
 * and the ordering can be unit-tested on the JVM. Titles, summaries and icons
 * are attached in the UI layer.
 */
enum class SettingsCategory(val id: String) {
    APPEARANCE("appearance"),
    PLAYER("player"),
    DOWNLOADS("downloads"),
    PRIVACY("privacy"),
    STATISTICS("statistics"),
    EXTENSIONS("extensions"),
    MANGA("manga"),
    ABOUT("about"),
    ;

    companion object {
        /** Nav argument name used by the category route. */
        const val ARG_ID = "id"

        /** Route pattern for a category page, registered in both navigation graphs. */
        const val ROUTE = "settings_page/{$ARG_ID}"

        /** The full-screen palette picker, pushed above the Appearance category. */
        const val COLORS_ROUTE = "settings_colors"

        /** The full-screen launcher icon picker, pushed above the Appearance category. */
        const val APP_ICON_ROUTE = "settings_app_icon"

        fun fromId(id: String?): SettingsCategory? = entries.firstOrNull { it.id == id }

        fun route(category: SettingsCategory): String = "settings_page/${category.id}"

        /**
         * Visual clusters on the root page. Every category appears exactly once;
         * the clusters only decide where the larger gaps go.
         */
        val clusters: List<List<SettingsCategory>> = listOf(
            listOf(APPEARANCE, PLAYER, DOWNLOADS),
            listOf(PRIVACY, STATISTICS),
            listOf(EXTENSIONS, MANGA),
            listOf(ABOUT),
        )
    }
}
