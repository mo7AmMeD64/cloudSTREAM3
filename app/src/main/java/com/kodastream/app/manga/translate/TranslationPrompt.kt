package com.kodastream.app.manga.translate

/** Bumped when the prompt changes so cached pages are translated again. */
const val TRANSLATION_PROMPT_VERSION = 2

private const val MAX_CONTEXT_LINES = 10
private const val MAX_CONTEXT_LINE_LENGTH = 160

fun translationLanguageName(target: String): String =
    if (target == "en") "English" else "Arabic"

/** Chapter memory handed to every page so names and pronouns stay consistent. */
data class TranslationContext(
    val seriesTitle: String? = null,
    /** Earlier bubbles of the same chapter, original and already translated. */
    val previous: List<TranslatedBlock> = emptyList(),
)

fun buildTranslationPrompt(target: String, context: TranslationContext): String {
    val language = translationLanguageName(target)
    return buildString {
        appendLine("You are a professional manga and manhwa translator.")
        appendLine(
            "Translate every speech bubble, thought bubble, narration box and " +
                "story-relevant sign in this page image into $language.",
        )
        appendLine()
        appendLine("Rules:")
        appendLine(
            "- Write natural, modern $language that reads like a published manga " +
                "translation. Keep the meaning and tone of the original instead of " +
                "translating word for word.",
        )
        appendLine(
            "- Keep character names, nicknames, places, titles and recurring terms " +
                "unchanged and spelled the same way on every page.",
        )
        appendLine(
            "- Keep each line about as long as the original so the translation fits " +
                "the bubble. Prefer short, punchy lines over long explanations.",
        )
        appendLine("- Never add translator notes, explanations, romanization or commentary.")
        appendLine(
            "- Do not translate sound effects, onomatopoeia, or text that is already " +
                "written in $language.",
        )
        appendLine("- Return one block per bubble or text region, in reading order.")
        appendLine(
            "- box_2d is [ymin, xmin, ymax, xmax] with integers from 0 to 1000 relative " +
                "to the image. Keep it tight around the printed text, inside the bubble, " +
                "and never include artwork.",
        )
        appendLine("- text is the original text exactly as printed; translation is the $language text.")
        appendLine("- type is one of dialogue, narration, sign or sfx.")
        if (language == "Arabic") {
            appendLine(
                "- Use Modern Standard Arabic. Keep honorifics and untranslatable terms " +
                    "by transliterating them in Arabic letters.",
            )
        }
        context.seriesTitle?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appendLine()
            appendLine("Series: ${it.take(120)}")
        }
        val previous = context.previous
            .filter { it.text.isNotBlank() && it.translation.isNotBlank() }
            .takeLast(MAX_CONTEXT_LINES)
        if (previous.isNotEmpty()) {
            appendLine()
            appendLine(
                "Earlier in this chapter (original -> translation). Keep names, " +
                    "pronouns and tone consistent with these:",
            )
            previous.forEach {
                appendLine("- ${it.text.oneLine()}: ${it.translation.oneLine()}")
            }
        }
        appendLine()
        append("Reply with a single JSON object and nothing else: ")
        append(
            """{"blocks":[{"box_2d":[ymin,xmin,ymax,xmax],""" +
                """"text":"original","translation":"$language text","type":"dialogue"}]}""",
        )
    }
}

private fun String.oneLine(): String =
    replace('\n', ' ').trim().take(MAX_CONTEXT_LINE_LENGTH)
