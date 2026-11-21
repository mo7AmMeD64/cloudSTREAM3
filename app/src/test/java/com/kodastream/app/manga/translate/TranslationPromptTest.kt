package com.kodastream.app.manga.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPromptTest {

    @Test
    fun promptNamesTheTargetLanguageAndTheSeries() {
        val prompt = buildTranslationPrompt(
            target = "ar",
            context = TranslationContext(
                seriesTitle = "Blue Lock",
                previous = listOf(
                    TranslatedBlock(text = "Yuki", translation = "يوكي", type = "dialogue"),
                ),
            ),
        )

        assertTrue(prompt.contains("into Arabic"))
        assertTrue(prompt.contains("Modern Standard Arabic"))
        assertTrue(prompt.contains("Series: Blue Lock"))
        assertTrue(prompt.contains("Yuki: يوكي"))
        assertTrue(prompt.contains("\"blocks\""))
    }

    @Test
    fun englishPromptDoesNotCarryArabicRules() {
        val prompt = buildTranslationPrompt("en", TranslationContext())

        assertTrue(prompt.contains("into English"))
        assertFalse(prompt.contains("Modern Standard Arabic"))
        assertFalse(prompt.contains("Earlier in this chapter"))
    }

    @Test
    fun onlyTheMostRecentLinesAreCarried() {
        val previous = (1..15).map {
            TranslatedBlock(text = "line $it", translation = "سطر $it", type = "dialogue")
        }

        val prompt = buildTranslationPrompt("ar", TranslationContext(previous = previous))

        assertFalse(prompt.contains("- line 1:"))
        assertFalse(prompt.contains("- line 5:"))
        assertTrue(prompt.contains("- line 6:"))
        assertTrue(prompt.contains("- line 15:"))
    }

    @Test
    fun longContextLinesAreTruncated() {
        val prompt = buildTranslationPrompt(
            "ar",
            TranslationContext(
                previous = listOf(
                    TranslatedBlock(
                        text = "x".repeat(500),
                        translation = "س".repeat(500),
                        type = "dialogue",
                    ),
                ),
            ),
        )

        assertFalse(prompt.contains("x".repeat(161)))
        assertFalse(prompt.contains("س".repeat(161)))
    }

    @Test
    fun blankContextPairsAreIgnored() {
        val prompt = buildTranslationPrompt(
            "ar",
            TranslationContext(
                previous = listOf(TranslatedBlock(text = "only original", translation = "")),
            ),
        )

        assertFalse(prompt.contains("only original"))
    }
}
