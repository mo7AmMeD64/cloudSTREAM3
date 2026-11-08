package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import okhttp3.Headers
import java.util.Locale

// Local copy of Mihon's WebView helpers (Apache-2.0), without logcat/i18n dependencies.
object WebViewUtil {
    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val YOUTUBE_FOR_TV_PACKAGE = "com.google.android.youtube.tv"
    private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"

    const val MINIMUM_WEBVIEW_VERSION = 118

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw WebViewFactory$MissingWebViewPackageException if WebView is missing.
            CookieManager.getInstance()
        } catch (e: Throwable) {
            Log.e("KodaManga", "WebView is not available", e)
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }
}

fun WebView.isOutdated(): Boolean {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    val major = if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
    return major < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return

    val versionMatch = CHROME_VERSION_REGEX.find(userAgent) ?: return
    val majorVersion = versionMatch.groupValues[1]
    val fullVersion = majorVersion + versionMatch.groupValues[2].ifEmpty { ".0.0.0" }

    try {
        val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
        val brandVersionList = metadata.brandVersionList.map { brandVersion ->
            val brand = when (brandVersion.brand) {
                WEBVIEW_BRAND -> CHROME_BRAND
                CHROMIUM_BRAND -> CHROMIUM_BRAND
                else -> return@map brandVersion
            }

            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build()
        }

        WebSettingsCompat.setUserAgentMetadata(
            settings,
            UserAgentMetadata.Builder(metadata)
                .setBrandVersionList(brandVersionList)
                .setFullVersion(fullVersion)
                .build(),
        )
    } catch (e: Exception) {
        Log.e("KodaManga", "Failed to set user agent metadata", e)
    }
}

fun Headers.toWebViewHeaders(): Map<String, String> {
    return this
        .filter { (name, value) -> isRequestHeaderSafe(name, value) }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.getOrNull(0).orEmpty() }
}

private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}

private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive",
    "transfer-encoding", "set-cookie", "origin",
)

private const val WEBVIEW_BRAND = "Android WebView"
private const val CHROMIUM_BRAND = "Chromium"
private const val CHROME_BRAND = "Google Chrome"
private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()

private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString
    settings.userAgentString = originalUA
    return defaultUserAgentString
}
