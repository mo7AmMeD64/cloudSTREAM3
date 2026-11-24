package com.kodastream.app.data

import com.kodastream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The only place that talks to Supabase. The publishable key is public by
 * design; row security on the server is what protects each account's data.
 */
internal object SupabaseHttp {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Code 0 means the request never reached the server. */
    class Reply(val code: Int, val json: JSONObject, val raw: String = "") {
        val ok: Boolean get() = code in 200..299
    }

    suspend fun send(
        method: String,
        path: String,
        body: JSONObject? = null,
        bearer: String? = null,
        query: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Reply = withContext(Dispatchers.IO) {
        if (!configured) return@withContext Reply(0, JSONObject())
        val url = BuildConfig.SUPABASE_URL.trimEnd('/') + path + (query?.let { "?$it" } ?: "")
        val payload = body?.toString()
        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Accept", "application/json")
            .apply {
                if (bearer != null) header("Authorization", "Bearer $bearer")
                headers.forEach { (name, value) -> header(name, value) }
                method(
                    method,
                    when {
                        payload != null -> payload.toRequestBody(JSON)
                        method == "POST" || method == "PATCH" -> ByteArray(0).toRequestBody(JSON)
                        else -> null
                    },
                )
            }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                Reply(response.code, json, text)
            }
        } catch (_: IOException) {
            Reply(0, JSONObject())
        }
    }
}
