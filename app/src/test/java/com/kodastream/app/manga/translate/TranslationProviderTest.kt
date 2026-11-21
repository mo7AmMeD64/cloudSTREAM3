package com.kodastream.app.manga.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationProviderTest {

    @Test
    fun baseUrlGetsTheChatCompletionsPath() {
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            chatCompletionsUrl("https://api.groq.com/openai/v1"),
        )
        assertEquals(
            "http://192.168.1.50:11434/v1/chat/completions",
            chatCompletionsUrl(" http://192.168.1.50:11434/v1/ "),
        )
    }

    @Test
    fun fullEndpointIsNotRepeated() {
        assertEquals(
            "https://host/v1/chat/completions",
            chatCompletionsUrl("https://host/v1/chat/completions/"),
        )
    }

    @Test
    fun invalidBaseUrlsAreRejected() {
        assertNull(chatCompletionsUrl(""))
        assertNull(chatCompletionsUrl("192.168.1.50:11434/v1"))
        assertNull(chatCompletionsUrl("ftp://host/v1"))
        assertNull(chatCompletionsUrl("https://host/v1/chat/completions/chat/completions"))
    }

    @Test
    fun geminiIsTheOnlyNativeProvider() {
        assertTrue(TranslationProvider.GEMINI.isGemini)
        assertFalse(TranslationProvider.GROK.isGemini)
        assertFalse(TranslationProvider.CUSTOM.isGemini)
    }

    @Test
    fun unknownProviderIdsFallBackToGemini() {
        assertEquals(TranslationProvider.GEMINI, TranslationProvider.fromId(null))
        assertEquals(TranslationProvider.GEMINI, TranslationProvider.fromId("deleted-provider"))
    }

    @Test
    fun cacheSignatureCoversTheWholeBackend() {
        val base = TranslationConfig(
            provider = TranslationProvider.GEMINI,
            apiKey = "key",
            model = "gemini-2.5-flash",
            baseUrl = "",
            target = "ar",
        )

        assertNotEquals(base.cacheSignature, base.copy(target = "en").cacheSignature)
        assertNotEquals(base.cacheSignature, base.copy(model = "gemini-2.5-flash-lite").cacheSignature)
        assertNotEquals(
            base.cacheSignature,
            base.copy(provider = TranslationProvider.OPENROUTER).cacheSignature,
        )
        assertNotEquals(
            base.cacheSignature,
            base.copy(baseUrl = "http://192.168.1.50:11434/v1").cacheSignature,
        )
        val same = base.copy(apiKey = "another-key")
        assertEquals(base.cacheSignature, same.cacheSignature)
    }

    @Test
    fun onlyCustomServersSkipTheQuotaSpacing() {
        val base = TranslationConfig(
            provider = TranslationProvider.CUSTOM,
            apiKey = "",
            model = "qwen",
            baseUrl = "http://192.168.1.50:11434/v1",
            target = "ar",
        )

        assertEquals(500L, base.requestSpacingMs)
        assertTrue(base.copy(provider = TranslationProvider.GROQ).requestSpacingMs >= 4_000L)
    }

    @Test
    fun everyCloudPresetHasABaseUrlAndModel() {
        TranslationProvider.values()
            .filter { it != TranslationProvider.CUSTOM && !it.isGemini }
            .forEach {
                assertTrue(it.id, it.defaultBaseUrl.startsWith("https://"))
                assertTrue(it.id, it.defaultModel.isNotBlank())
            }
    }
}
