package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OkHttp client used by Mihon extensions (Apache-2.0), ported from OkHttp 5 to 4.12.
 */
class NetworkHelper(
    context: Context,
    private val preferences: NetworkPreferences,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val cookieJar = AndroidCookieJar()

    private fun clientBuilder(): OkHttpClient.Builder = mihonClientBuilder(
        cacheDirectory = File(appContext.cacheDir, "network_cache"),
        cookieJar = cookieJar,
        userAgent = ::defaultUserAgentProvider,
    ).apply {
        if (preferences.verboseLogging) {
            // Logging is intentionally not wired to okhttp-logging: it is not a
            // dependency of the app and extensions don't use it.
        }
    }

    val client: OkHttpClient by lazy {
        clientBuilder()
            .addInterceptor(
                CloudflareInterceptor(appContext, cookieJar, scope, ::defaultUserAgentProvider),
            )
            .build()
    }

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.trim()
}

/** The base chain, without the Android Cloudflare interceptor, so unit tests can build it. */
internal fun mihonClientBuilder(
    cacheDirectory: File,
    cookieJar: okhttp3.CookieJar,
    userAgent: () -> String,
): OkHttpClient.Builder = OkHttpClient.Builder()
    .cookieJar(cookieJar)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(2, TimeUnit.MINUTES)
    .cache(
        Cache(
            directory = cacheDirectory,
            maxSize = 5L * 1024 * 1024,
        ),
    )
    .addInterceptor(UncaughtExceptionInterceptor())
    .addInterceptor(UserAgentInterceptor(userAgent))

