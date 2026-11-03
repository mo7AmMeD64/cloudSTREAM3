package com.kodastream.app.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaPageCacheTest {
    private fun file(path: String, size: Long, modified: Long) =
        CachedFile(path = path, size = size, lastModified = modified)

    @Test
    fun cacheUnderTheLimitEvictsNothing() {
        val files = listOf(file("a", 10, 1), file("b", 20, 2))

        assertTrue(pageCacheEvictions(files, 30).isEmpty())
    }

    @Test
    fun oldestFilesGoFirstUntilTheCacheFits() {
        val files = listOf(
            file("newest", 10, 30),
            file("oldest", 10, 10),
            file("middle", 10, 20),
        )

        assertEquals(
            listOf("oldest", "middle"),
            pageCacheEvictions(files, maxBytes = 15),
        )
    }

    @Test
    fun beingExactlyAtTheLimitEvictsNothing() {
        assertTrue(pageCacheEvictions(listOf(file("a", 10, 1), file("b", 10, 2)), 20).isEmpty())
    }

    @Test
    fun oneOversizedFileIsEvictedEntirely() {
        assertEquals(listOf("huge"), pageCacheEvictions(listOf(file("huge", 500, 1)), 200))
    }

    @Test
    fun emptyCacheHasNothingToEvict() {
        assertTrue(pageCacheEvictions(emptyList(), 0).isEmpty())
    }
}
