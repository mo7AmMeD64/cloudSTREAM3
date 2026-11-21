package com.kodastream.app.manga.translate

/**
 * Translation backends. Gemini has its own vision API; every other provider
 * speaks the OpenAI chat-completions shape, so one adapter covers them all.
 */
enum class TranslationProvider(
    val id: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    GEMINI("gemini", "", "gemini-2.5-flash"),
    GROK("grok", "https://api.x.ai/v1", "grok-4.7"),
    GROQ("groq", "https://api.groq.com/openai/v1", "meta-llama/llama-4-scout-17b-16e-instruct"),
    OPENROUTER("openrouter", "https://openrouter.ai/api/v1", "google/gemma-4-31b-it:free"),
    MISTRAL("mistral", "https://api.mistral.ai/v1", "mistral-large-4"),
    CUSTOM("custom", "", "");

    val isGemini: Boolean get() = this == GEMINI

    companion object {
        fun fromId(id: String?): TranslationProvider =
            values().firstOrNull { it.id == id } ?: GEMINI
    }
}

/** Everything one translation run needs; copied once so a settings change mid-run is ignored. */
data class TranslationConfig(
    val provider: TranslationProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val target: String,
) {
    /** Everything that changes the answer belongs in the cache key. */
    val cacheSignature: String
        get() = "${provider.id}|$baseUrl|$model|$target|$TRANSLATION_PROMPT_VERSION"

    /** Local servers have no quota to protect; free cloud tiers need the spacing. */
    val requestSpacingMs: Long
        get() = if (provider == TranslationProvider.CUSTOM) 500L else 4_000L
}

/**
 * Accepts `https://host/v1`, `https://host/v1/` or a full `.../chat/completions`
 * URL, and returns the API root without the endpoint.
 */
internal fun openAiApiRoot(baseUrl: String): String? {
    val base = baseUrl.trim().trimEnd('/')
    if (!base.startsWith("http://") && !base.startsWith("https://")) return null
    val root = base.removeSuffix("/chat/completions").trimEnd('/')
    // A doubled endpoint means a full URL was pasted where a base URL belongs.
    if (root.endsWith("/chat/completions")) return null
    return root.takeIf { it.isNotEmpty() }
}

internal fun chatCompletionsUrl(baseUrl: String): String? =
    openAiApiRoot(baseUrl)?.let { "$it/chat/completions" }
