package com.kodastream.app.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaFiltersTest {
    private class TextFilter(name: String, value: String = "") : Filter.Text(name, value)
    private class CheckFilter(name: String, value: Boolean = false) : Filter.CheckBox(name, value)
    private class TriFilter(name: String, value: Int = Filter.TriState.STATE_IGNORE) :
        Filter.TriState(name, value)

    private class SelectFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    private class SortFilter(
        name: String,
        values: Array<String>,
        state: Filter.Sort.Selection? = null,
    ) : Filter.Sort(name, values, state)

    private class GroupFilter(name: String, filters: List<Filter<*>>) :
        Filter.Group<Filter<*>>(name, filters)

    private fun buildFilters(
        text: String = "",
        check: Boolean = false,
        tri: Int = Filter.TriState.STATE_IGNORE,
        select: Int = 0,
        sort: Filter.Sort.Selection? = null,
        nestedText: String = "",
        nestedCheck: Boolean = false,
    ) = FilterList(
        TextFilter("text", text),
        CheckFilter("check", check),
        TriFilter("tri", tri),
        SelectFilter("select", arrayOf("a", "b", "c"), select),
        SortFilter("sort", arrayOf("name", "date"), sort),
        GroupFilter(
            "group",
            listOf(TextFilter("nested-text", nestedText), CheckFilter("nested-check", nestedCheck)),
        ),
        Filter.Header("header"),
        Filter.Separator(),
    )

    @Test
    fun cancelRestoresEveryValueIncludingGroups() {
        val filters = buildFilters()
        val snapshot = snapshotFilters(filters)

        (filters[0] as Filter.Text).state = "typed"
        (filters[1] as Filter.CheckBox).state = true
        (filters[2] as Filter.TriState).state = Filter.TriState.STATE_EXCLUDE
        (filters[3] as Filter.Select<*>).state = 2
        (filters[4] as Filter.Sort).state = Filter.Sort.Selection(1, false)
        val group = (filters[5] as Filter.Group<*>).state.filterIsInstance<Filter<*>>()
        (group[0] as Filter.Text).state = "changed"
        (group[1] as Filter.CheckBox).state = true

        restoreFilters(filters, snapshot)

        assertEquals("", (filters[0] as Filter.Text).state)
        assertFalse((filters[1] as Filter.CheckBox).state)
        assertEquals(Filter.TriState.STATE_IGNORE, (filters[2] as Filter.TriState).state)
        assertEquals(0, (filters[3] as Filter.Select<*>).state)
        assertEquals(null, (filters[4] as Filter.Sort).state)
        val restored = (filters[5] as Filter.Group<*>).state.filterIsInstance<Filter<*>>()
        assertEquals("", (restored[0] as Filter.Text).state)
        assertFalse((restored[1] as Filter.CheckBox).state)
    }

    @Test
    fun snapshotCapturesTheCurrentState() {
        val filters = buildFilters(
            text = "one",
            check = true,
            tri = Filter.TriState.STATE_INCLUDE,
            select = 1,
            sort = Filter.Sort.Selection(1, true),
            nestedText = "nested",
            nestedCheck = true,
        )

        val snapshot = snapshotFilters(filters)

        assertEquals(FilterSnapshot.Text("one"), snapshot[0])
        assertEquals(FilterSnapshot.Check(true), snapshot[1])
        assertEquals(FilterSnapshot.Tri(Filter.TriState.STATE_INCLUDE), snapshot[2])
        assertEquals(FilterSnapshot.Select(1), snapshot[3])
        assertEquals(FilterSnapshot.Sort(Filter.Sort.Selection(1, true)), snapshot[4])
        assertEquals(
            FilterSnapshot.Group(
                listOf(FilterSnapshot.Text("nested"), FilterSnapshot.Check(true)),
            ),
            snapshot[5],
        )
        assertEquals(FilterSnapshot.None, snapshot[6])
        assertEquals(FilterSnapshot.None, snapshot[7])
    }

    @Test
    fun resetReturnsEverythingToItsDefault() {
        val filters = buildFilters(
            text = "one",
            check = true,
            tri = Filter.TriState.STATE_EXCLUDE,
            select = 2,
            sort = Filter.Sort.Selection(1, false),
            nestedText = "nested",
            nestedCheck = true,
        )

        resetFilters(filters)

        assertEquals(0, activeFilterCount(filters))
    }

    @Test
    fun activeCountIncludesEnabledAndNestedFilters() {
        assertEquals(0, activeFilterCount(buildFilters()))
        assertEquals(5, activeFilterCount(buildFilters(text = "one", check = true, tri = 1, select = 1, sort = Filter.Sort.Selection(0, true))))
        assertEquals(1, activeFilterCount(buildFilters(nestedCheck = true)))
    }

    @Test
    fun headerAndSeparatorOnlyListsHaveNoUsableFilters() {
        assertFalse(hasUsableFilters(FilterList(Filter.Header("h"), Filter.Separator())))
        assertTrue(hasUsableFilters(FilterList(Filter.Header("h"), TextFilter("t"))))
    }
}
