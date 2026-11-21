package com.kodastream.app.manga.translate

import android.graphics.Bitmap
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A failed translation call; [code] is the HTTP status, or 0 for a bad response. */
class TranslationException(val code: Int, message: String) : Exception(message)

/**
 * One page per request: Gemini spots the text and translates it in the same
 * vision call, which avoids running a local OCR pipeline just to use the API.
 * The reader only sends the page the user asked for.
 */
class GeminiTranslator(private val client: OkHttpClient = defaultClient) {

    suspend fun translate(
        bitmap: Bitmap,
        target: String,
        apiKey: String,
        model: String,
        context: TranslationContext = TranslationContext(),
    ): List<TranslatedBlock> {
        val image = withContext(Dispatchers.Default) { jpegBase64(bitmap) }
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", buildTranslationPrompt(target, context))
                        }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema())
                put("temperature", 0.2)
            }
        }
        val response = client.newCall(
            request(apiKey, model, "generateContent")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        ).await()
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw TranslationException(it.code, apiError(it.code, text))
            return parseResponse(text)
        }
    }

    /** A models.get call: it validates the key and the model without using generation quota. */
    suspend fun checkKey(apiKey: String, model: String) {
        val response = client.newCall(
            Request.Builder()
                .url(modelUrl(model) ?: throw TranslationException(0, "Invalid model id"))
                .header("x-goog-api-key", apiKey)
                .get()
                .build(),
        ).await()
        response.use {
            if (!it.isSuccessful) {
                throw TranslationException(it.code, apiError(it.code, it.body?.string().orEmpty()))
            }
        }
    }

    private fun request(apiKey: String, model: String, action: String): Request.Builder {
        val url = modelUrl(model) ?: throw TranslationException(0, "Invalid model id")
        return Request.Builder()
            .url("$url:$action")
            .header("x-goog-api-key", apiKey)
    }

    /** Model ids are plain names; anything else would build a different URL. */
    private fun modelUrl(model: String): String? {
        val id = model.trim().removePrefix("models/")
        if (id.isEmpty() || !id.matches(MODEL_ID)) return null
        return "https://generativelanguage.googleapis.com/v1beta/models/$id"
    }

    private fun responseSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("blocks") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("box_2d") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "INTEGER") }
                        }
                        putJsonObject("text") { put("type", "STRING") }
                        putJsonObject("translation") { put("type", "STRING") }
                        putJsonObject("type") {
                            put("type", "STRING")
                            put(
                                "enum",
                                JsonArray(
                                    listOf("dialogue", "narration", "sign", "sfx")
                                        .map { JsonPrimitive(it) },
                                ),
                            )
                        }
                    }
                    put(
                        "required",
                        JsonArray(listOf(JsonPrimitive("box_2d"), JsonPrimitive("translation"))),
                    )
                }
            }
        }
        put("required", JsonArray(listOf(JsonPrimitive("blocks"))))
    }

    private fun parseResponse(text: String): List<TranslatedBlock> {
        val root = runCatching {
            TranslationJson.json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: throw TranslationException(0, "Gemini returned unreadable JSON")
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val raw = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("") { part ->
                part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
            }
            .orEmpty()
        if (raw.isBlank()) {
            val reason = candidate?.get("finishReason")?.jsonPrimitive?.content
            throw TranslationException(
                0,
                if (reason == "SAFETY") "The page was blocked by the safety filter" else "Gemini returned no text",
            )
        }
        return TranslationJson.parseBlocks(raw)
    }

    private fun apiError(code: Int, body: String): String {
        val detail = runCatching {
            TranslationJson.json.parseToJsonElement(body)
                .jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        val base = when (code) {
            400 -> "Gemini rejected the request"
            401, 403 -> "The API key was rejected"
            404 -> "The model was not found"
            429 -> "The free Gemini quota is exhausted"
            else -> "Gemini error $code"
        }
        return if (detail.isNullOrBlank()) base else "$base: $detail"
    }

    private companion object {
        val MODEL_ID = Regex("[A-Za-z0-9._-]+")
        val JSON = "application/json; charset=utf-8".toMediaType()
        val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
