package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test
    fun newestFirst() {
        assertEquals(listOf("beta", "alpha"), addSearchHistory(listOf("alpha"), "beta"))
    }

    @Test
    fun trimsAndDedupesCaseInsensitively() {
        assertEquals(
            listOf("Star Wars", "dune"),
            addSearchHistory(listOf("dune", "star wars"), "  Star Wars "),
        )
    }

    @Test
    fun ignoresShortOrBlankQueries() {
        val current = listOf("dune")
        assertEquals(current, addSearchHistory(current, " "))
        assertEquals(current, addSearchHistory(current, "a"))
        assertEquals(current, addSearchHistory(current, "  a  "))
    }

    @Test
    fun capsAtMax() {
        val current = (1..20).map { "q$it" }
        val next = addSearchHistory(current, "new")
        assertEquals(20, next.size)
        assertEquals("new", next.first())
        assertEquals("q19", next.last())
    }
}
