package com.kodastream.app.manga.translate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val TRANSLATION_CACHE_VERSION = 2

/** One text region as a model returns it; [box] is [ymin, xmin, ymax, xmax] 0..1000. */
@Serializable
data class TranslatedBlock(
    @SerialName("box_2d") val box: List<Float> = emptyList(),
    val text: String = "",
    val translation: String = "",
    val type: String = "",
    /** Sampled bubble fill as an ARGB colour, or 0 when the page could not be sampled. */
    @SerialName("bg") val background: Int = 0,
) {
    /** The box as [left, top, right, bottom] fractions of the image, or null if unusable. */
    fun normalizedBox(): FloatArray? {
        if (box.size < 4) return null
        val xmin = box[1]
        val ymin = box[0]
        val xmax = box[3]
        val ymax = box[2]
        if (!xmin.isFinite() || !ymin.isFinite() || !xmax.isFinite() || !ymax.isFinite()) {
            return null
        }
        val left = minOf(xmin, xmax) / 1000f
        val top = minOf(ymin, ymax) / 1000f
        val right = maxOf(xmin, xmax) / 1000f
        val bottom = maxOf(ymin, ymax) / 1000f
        val clampedLeft = left.coerceIn(0f, 1f)
        val clampedTop = top.coerceIn(0f, 1f)
        val clampedRight = right.coerceIn(0f, 1f)
        val clampedBottom = bottom.coerceIn(0f, 1f)
        if (clampedRight - clampedLeft < 0.005f || clampedBottom - clampedTop < 0.005f) {
            return null
        }
        return floatArrayOf(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }
}

@Serializable
data class PageTranslation(
    val version: Int = TRANSLATION_CACHE_VERSION,
    val blocks: List<TranslatedBlock> = emptyList(),
)

@Serializable
private data class BlocksResponse(val blocks: List<TranslatedBlock> = emptyList())

object TranslationJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Gemini is asked for JSON, but models occasionally wrap it in a fence or
     * add a sentence around it, so the object is cut out before decoding.
     */
    fun parseBlocks(raw: String): List<TranslatedBlock> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            json.decodeFromString<BlocksResponse>(raw.substring(start, end + 1)).blocks
        }.getOrDefault(emptyList())
            .filter { it.translation.isNotBlank() && it.normalizedBox() != null }
    }

    /** The assistant text out of an OpenAI-compatible chat completion. */
    fun chatCompletionText(raw: String): String? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")
        when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("").takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()

    fun encode(translation: PageTranslation): String = json.encodeToString(translation)

    fun decode(raw: String): PageTranslation? = runCatching {
        json.decodeFromString<PageTranslation>(raw)
            .takeIf { it.version == TRANSLATION_CACHE_VERSION }
    }.getOrNull()
}

/**
 * A box from one webtoon slice, moved into the full page's normalized space.
 * A slice spans the full width, so only the vertical range changes.
 */
fun TranslatedBlock.moveToPage(sliceTop: Int, sliceHeight: Int, pageHeight: Int): TranslatedBlock? {
    val box = normalizedBox() ?: return null
    if (sliceHeight <= 0 || pageHeight <= 0) return null
    val top = (sliceTop + box[1] * sliceHeight) / pageHeight * 1000f
    val bottom = (sliceTop + box[3] * sliceHeight) / pageHeight * 1000f
    return copy(box = listOf(top, box[0] * 1000f, bottom, box[2] * 1000f))
}
