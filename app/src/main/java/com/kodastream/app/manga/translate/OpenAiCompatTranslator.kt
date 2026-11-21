package com.kodastream.app.manga.translate

import android.graphics.Bitmap
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
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

/**
 * One adapter for every OpenAI-compatible provider: xAI, Groq, OpenRouter,
 * Mistral, Ollama, LM Studio and any custom `/v1` server. The page is sent as
 * a vision message and the reply is parsed with the same tolerant JSON reader
 * as Gemini, so no provider-specific response schema is required.
 */
class OpenAiCompatTranslator(private val client: OkHttpClient = defaultClient) {

    suspend fun translate(
        bitmap: Bitmap,
        config: TranslationConfig,
        context: TranslationContext = TranslationContext(),
    ): List<TranslatedBlock> {
        val url = chatCompletionsUrl(config.baseUrl)
            ?: throw TranslationException(0, "Invalid API base URL")
        val image = withContext(Dispatchers.Default) { jpegBase64(bitmap) }
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", buildTranslationPrompt(config.target, context))
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$image")
                            }
                        }
                    }
                }
            }
        }
        val request = request(url, config.apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = client.newCall(request).await()
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw TranslationException(it.code, apiError(it.code, text))
            val raw = TranslationJson.chatCompletionText(text)
                ?: throw TranslationException(0, "The provider returned an unexpected response")
            if (!raw.contains('{')) {
                throw TranslationException(0, "The model did not return the expected JSON")
            }
            return TranslationJson.parseBlocks(raw)
        }
    }

    /**
     * A models list call: validates the key and the base URL without spending
     * generation quota. Every OpenAI-compatible server supports this endpoint.
     */
    suspend fun check(apiKey: String, baseUrl: String) {
        val root = openAiApiRoot(baseUrl)
            ?: throw TranslationException(0, "Invalid API base URL")
        val response = client.newCall(
            request("$root/models", apiKey).get().build(),
        ).await()
        response.use {
            if (!it.isSuccessful) {
                throw TranslationException(it.code, apiError(it.code, it.body?.string().orEmpty()))
            }
        }
    }

    private fun request(url: String, apiKey: String): Request.Builder {
        val builder = Request.Builder().url(url)
        // Local servers accept no key, so an empty key simply sends no header.
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder
    }

    private fun apiError(code: Int, body: String): String {
        val detail = runCatching {
            val root = TranslationJson.json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val base = when (code) {
            400 -> "The provider rejected the request"
            401, 403 -> "The API key was rejected"
            404 -> "The model or API base URL was not found"
            413 -> "The page image is too large for this model"
            422 -> "The provider could not read the image. Choose a vision model."
            429 -> "The provider's quota or rate limit is exhausted"
            else -> "Provider error $code"
        }
        return if (detail.isNullOrBlank()) base else "$base: $detail"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()

        const val SYSTEM_PROMPT =
            "You translate manga and manhwa pages. Follow the user's rules exactly " +
                "and reply with JSON only."
    }
}

/** The settings page's Test button: validates the saved settings without using a page. */
suspend fun testTranslationProvider(
    provider: TranslationProvider,
    apiKey: String,
    model: String,
    baseUrl: String,
) {
    if (provider.isGemini) {
        GeminiTranslator().checkKey(apiKey, model)
    } else {
        OpenAiCompatTranslator().check(apiKey, baseUrl)
    }
}
