package com.kodastream.app.manga.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TranslationCacheKeyTest {

    @Test
    fun sameBackendAndPageProduceTheSameKey() {
        val signature = "gemini||gemini-2.5-flash|ar|2"

        assertEquals(
            TranslationCache.key("page-hash", "p0", signature),
            TranslationCache.key("page-hash", "p0", signature),
        )
    }

    @Test
    fun switchingBackendInvalidatesTheKey() {
        val gemini = TranslationCache.key("page-hash", "p0", "gemini||gemini-2.5-flash|ar|2")
        val openRouter = TranslationCache.key(
            "page-hash",
            "p0",
            "openrouter|https://openrouter.ai/api/v1|google/gemma-4-31b-it:free|ar|2",
        )

        assertNotEquals(gemini, openRouter)
    }

    @Test
    fun changingThePromptVersionInvalidatesTheKey() {
        assertNotEquals(
            TranslationCache.key("page-hash", "p0", "gemini||gemini-2.5-flash|ar|1"),
            TranslationCache.key("page-hash", "p0", "gemini||gemini-2.5-flash|ar|2"),
        )
    }

    @Test
    fun splitPagesKeepSeparateKeys() {
        assertNotEquals(
            TranslationCache.key("page-hash", "p00", "gemini||gemini-2.5-flash|ar|2"),
            TranslationCache.key("page-hash", "p01", "gemini||gemini-2.5-flash|ar|2"),
        )
    }
}
