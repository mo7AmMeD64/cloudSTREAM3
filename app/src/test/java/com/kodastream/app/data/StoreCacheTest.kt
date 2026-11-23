package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreCacheTest {

    @Test
    fun loadsOncePerKey() {
        val cache = StoreCache<String>()
        var loads = 0
        assertEquals("a", cache.get("one") { loads++; "a" })
        assertEquals("a", cache.get("one") { loads++; "b" })
        assertEquals(1, loads)
    }

    @Test
    fun differentKeysDoNotShareTheValue() {
        val cache = StoreCache<String>()
        assertEquals("a", cache.get("one") { "a" })
        assertEquals("b", cache.get("two") { "b" })
    }

    @Test
    fun clearForcesAReload() {
        val cache = StoreCache<String>()
        var loads = 0
        cache.get("one") { loads++; "a" }
        cache.clear()
        assertEquals("b", cache.get("one") { loads++; "b" })
        assertEquals(2, loads)
    }
}
