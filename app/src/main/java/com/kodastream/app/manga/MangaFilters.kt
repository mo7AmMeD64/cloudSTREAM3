package com.kodastream.app.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

/**
 * A copy of a source's filter state. Sources commonly return the same mutable
 * filter objects, so the sheet edits a working list and restores this snapshot
 * on Cancel. Kept pure and index-based because [Filter] subclasses cannot be
 * cloned generically.
 */
sealed interface FilterSnapshot {
    data object None : FilterSnapshot
    data class Text(val value: String) : FilterSnapshot
    data class Check(val value: Boolean) : FilterSnapshot
    data class Tri(val value: Int) : FilterSnapshot
    data class Select(val index: Int) : FilterSnapshot
    data class Sort(val selection: Filter.Sort.Selection?) : FilterSnapshot
    data class Group(val children: List<FilterSnapshot>) : FilterSnapshot
}

fun snapshotFilters(filters: FilterList): List<FilterSnapshot> =
    filters.map { filter ->
        when (filter) {
            is Filter.Text -> FilterSnapshot.Text(filter.state)
            is Filter.CheckBox -> FilterSnapshot.Check(filter.state)
            is Filter.TriState -> FilterSnapshot.Tri(filter.state)
            is Filter.Select<*> -> FilterSnapshot.Select(filter.state)
            is Filter.Sort -> FilterSnapshot.Sort(filter.state)
            is Filter.Group<*> -> FilterSnapshot.Group(
                snapshotFilters(FilterList(filter.state.filterIsInstance<Filter<*>>()))
            )
            is Filter.Header, is Filter.Separator -> FilterSnapshot.None
        }
    }

@Suppress("UNCHECKED_CAST")
fun restoreFilters(filters: FilterList, snapshots: List<FilterSnapshot>) {
    filters.forEachIndexed { index, filter ->
        when (val snapshot = snapshots.getOrNull(index)) {
            is FilterSnapshot.Text -> (filter as? Filter.Text)?.state = snapshot.value
            is FilterSnapshot.Check -> (filter as? Filter.CheckBox)?.state = snapshot.value
            is FilterSnapshot.Tri -> (filter as? Filter.TriState)?.state = snapshot.value
            is FilterSnapshot.Select -> (filter as? Filter.Select<*>)?.state = snapshot.index
            is FilterSnapshot.Sort -> (filter as? Filter.Sort)?.state = snapshot.selection
            is FilterSnapshot.Group -> (filter as? Filter.Group<*>)?.let { group ->
                val children = FilterList((group.state as List<Any?>).filterIsInstance<Filter<*>>())
                restoreFilters(children, snapshot.children)
            }
            FilterSnapshot.None, null -> Unit
        }
    }
}

/** Resets to the constructor defaults every extension filter agrees on. */
fun resetFilters(filters: FilterList) {
    filters.forEach { filter ->
        when (filter) {
            is Filter.Text -> filter.state = ""
            is Filter.CheckBox -> filter.state = false
            is Filter.TriState -> filter.state = Filter.TriState.STATE_IGNORE
            is Filter.Select<*> -> filter.state = 0
            is Filter.Sort -> filter.state = null
            is Filter.Group<*> -> resetFilters(
                FilterList(filter.state.filterIsInstance<Filter<*>>())
            )
            is Filter.Header, is Filter.Separator -> Unit
        }
    }
}

/** How many filters the sheet's Apply button should count as active. */
fun activeFilterCount(filters: FilterList): Int =
    filters.sumOf { filter ->
        when (filter) {
            is Filter.Text -> if (filter.state.isNotBlank()) 1 else 0
            is Filter.CheckBox -> if (filter.state) 1 else 0
            is Filter.TriState -> if (filter.state != Filter.TriState.STATE_IGNORE) 1 else 0
            is Filter.Select<*> -> if (filter.state != 0) 1 else 0
            is Filter.Sort -> if (filter.state != null) 1 else 0
            is Filter.Group<*> -> activeFilterCount(
                FilterList(filter.state.filterIsInstance<Filter<*>>())
            )
            is Filter.Header, is Filter.Separator -> 0
        }
    }

/** Headers and separators alone are not worth a Filters button. */
fun hasUsableFilters(filters: FilterList): Boolean =
    filters.any { it !is Filter.Header && it !is Filter.Separator }
