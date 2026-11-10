package eu.kanade.tachiyomi.network

import android.content.Context

/**
 * Small SharedPreferences-backed replacement for Mihon's preference store (Apache-2.0).
 * Only the network settings the engine actually needs are kept.
 */
class NetworkPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mihon_network", Context.MODE_PRIVATE)

    var verboseLogging: Boolean
        get() = prefs.getBoolean("verbose_logging", false)
        set(value) = prefs.edit().putBoolean("verbose_logging", value).apply()

    var redactSensitiveHeaders: Boolean
        get() = prefs.getBoolean("redact_sensitive_headers", true)
        set(value) = prefs.edit().putBoolean("redact_sensitive_headers", value).apply()

    /** One of the PREF_DOH_* ids from DohProviders.kt, or -1 for none. Needs an app restart. */
    var dohProvider: Int
        get() = prefs.getInt("doh_provider", -1)
        set(value) = prefs.edit().putInt("doh_provider", value).apply()

    var defaultUserAgent: String
        get() = prefs.getString("default_user_agent", DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT
        set(value) = prefs.edit().putString("default_user_agent", value).apply()

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/149.0.0.0 Mobile Safari/537.36"
    }
}
