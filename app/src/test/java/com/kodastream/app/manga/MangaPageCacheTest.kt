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

    @Test
    fun aGifHeaderIsAnimated() {
        val gif = java.io.File.createTempFile("koda-page-", ".img")
        gif.deleteOnExit()
        gif.writeBytes(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00))

        assertTrue(isAnimatedImage(gif))
    }

    @Test
    fun aPngHeaderIsNotAnimated() {
        val png = java.io.File.createTempFile("koda-page-", ".img")
        png.deleteOnExit()
        png.writeBytes(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00),
        )

        assertEquals(false, isAnimatedImage(png))
    }
}
