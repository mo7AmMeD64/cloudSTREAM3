package com.kodastream.app.manga

/** A source as the pickers see it, without Android or extension types. */
data class SourceChoice(
    val id: Long,
    val name: String,
    val language: String,
    val nsfw: Boolean = false,
)

/** One sticky-header section of the source picker. */
data class SourceGroupModel(
    val language: String,
    val pinned: Boolean = false,
    val sources: List<SourceChoice>,
)

/** Hidden sources never reach a picker, and a remembered id that disappeared falls back to the first. */
fun selectSource(
    sources: List<SourceChoice>,
    savedId: Long?,
    showNsfw: Boolean,
): SourceChoice? {
    val visible = sources.filter { showNsfw || !it.nsfw }
    return visible.firstOrNull { it.id == savedId } ?: visible.firstOrNull()
}

/**
 * Pinned sources first, then the app language, then the rest alphabetically,
 * with sources that have no language in an "Other" section at the end.
 */
fun groupSources(
    sources: List<SourceChoice>,
    appLanguage: String,
    pinnedIds: Set<Long>,
    showNsfw: Boolean,
    otherLabel: String = "other",
): List<SourceGroupModel> {
    val visible = sources
        .filter { showNsfw || !it.nsfw }
        .distinctBy { it.id }

    val pinned = visible
        .filter { it.id in pinnedIds }
        .sortedBy { it.name.lowercase() }
    val rest = visible.filterNot { it.id in pinnedIds }

    val groups = rest
        .groupBy { it.language.ifBlank { otherLabel }.lowercase() }
        .map { (language, list) ->
            SourceGroupModel(language, sources = list.sortedBy { it.name.lowercase() })
        }
        .sortedWith(
            compareBy(
                { it.language == otherLabel },
                { it.language != appLanguage.lowercase() },
                { it.language },
            )
        )

    return listOfNotNull(
        if (pinned.isNotEmpty()) SourceGroupModel("", pinned = true, sources = pinned) else null,
    ) + groups
}
