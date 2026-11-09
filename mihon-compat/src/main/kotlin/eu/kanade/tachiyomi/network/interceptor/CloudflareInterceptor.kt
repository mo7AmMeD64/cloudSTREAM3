package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CloudflareInterceptor(
    private val appContext: Context,
    private val cookieManager: AndroidCookieJar,
    scope: CoroutineScope,
    defaultUserAgentProvider: () -> String,
): WebViewInterceptor(appContext, scope, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(appContext)

    // Requests to a host share its lock while a challenge is solved under it alone, so concurrent requests wait for
    // one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    // A host whose challenge could not be solved fails fast for a while instead of making every queued request
    // (covers, pages, searches) wait its own turn at the WebView. Solving the check by hand in the in-app browser
    // sets a new clearance, which is checked before this, so it ends the window at once.
    private val failedUntil = ConcurrentHashMap<String, Long>()

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        // Checking the cf-mitigated header is the official way to detect a Cloudflare challenge:
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lock = hostLocks.computeIfAbsent(request.url.host) { ReentrantReadWriteLock() }

        val (response, clearance) = lock.read {
            val response = chain.proceed(request)
            if (!shouldIntercept(response)) return response
            response to clearance(request.url)
        }

        // write releases the read hold of a request made from within another to the same host, like an extension
        // refreshing a token, which would otherwise wait on itself
        lock.write {
            // Another request may have solved the challenge while this one waited
            if (isCleared(request.url, clearance)) {
                response.close()
            } else {
                val blockedUntil = failedUntil[request.url.host]
                if (blockedUntil != null && SystemClock.elapsedRealtime() < blockedUntil) return response
                if (!prepareWebView()) return response
                try {
                    solve(request, response, clearance)
                } catch (e: IOException) {
                    failedUntil[request.url.host] = SystemClock.elapsedRealtime() + FAIL_FAST_MS
                    throw e
                }
                failedUntil.remove(request.url.host)
            }
        }

        return chain.proceed(request)
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        solve(request, response, clearance(request.url))
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    private fun solve(request: Request, response: Response, clearance: String?) {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, clearance)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(appContext.getString(eu.kanade.tachiyomi.compat.R.string.mihon_compat_cloudflare_failed), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, originalClearance: String?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webview: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var challengeFailed = false
        var isWebViewOutdated = false
        var isRenderProcessGone = false

        val origRequestUrl = originalRequest.url.toString()
        // A WebView can only load a page with GET, and loading the url of another method usually reaches an
        // endpoint that answers GET without a challenge to solve. The clearance covers the whole site, so it's
        // earned on the site's root page instead.
        val challengeUrl = if (originalRequest.method == "GET") {
            origRequestUrl
        } else {
            originalRequest.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        }
        val headers = originalRequest.headers.toWebViewHeaders()

        executor.execute {
            webview = createWebView(originalRequest)

            fun onChallengeEvent(event: String) {
                when (event) {
                    // The challenge cannot be solved non-interactively, abort.
                    "interactiveBegin" -> latch.countDown()
                    "fail" -> {
                        challengeFailed = true
                        latch.countDown()
                    }
                }
            }

            webview.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun postMessage(event: String) = onChallengeEvent(event)
                },
                "mihon",
            )

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (!challengeFailed && isCleared(originalRequest.url, originalClearance)) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == challengeUrl) {
                        if (!challengeFound) {
                            // The first request didn't return the challenge, abort.
                            latch.countDown()
                        } else {
                            view.evaluateJavascript(listenerScript, null)
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.responseHeaders["cf-mitigated"] == "challenge") {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Not handling it kills the app, failing the bypass is enough
                    Log.e(
                        "KodaManga",
                        "WebView renderer gone during Cloudflare bypass: crashed=${detail.didCrash()}, " +
                            "priority=${detail.rendererPriorityAtExit()}, url=$origRequestUrl",
                    )
                    isRenderProcessGone = true
                    latch.countDown()
                    return true
                }
            }

            webview.loadUrl(challengeUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            // Only destroy can be called on a WebView whose renderer is gone
            if (!cloudflareBypassed && !isRenderProcessGone) {
                isWebViewOutdated = webview?.isOutdated() == true
            }

            webview?.run {
                if (!isRenderProcessGone) {
                    removeJavascriptInterface("mihon")
                    stopLoading()
                }
                destroy()
            }
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // A clearance from a failed challenge would only be rejected again
            cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                appContext.toast(eu.kanade.tachiyomi.compat.R.string.mihon_compat_webview_outdated, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

// An origin rule without a port only matches the scheme's default one
private fun HttpUrl.origin(): String {
    return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private const val FAIL_FAST_MS = 60_000L

/** The check could not be passed without the user; the app offers its in-app browser when it sees this. */
class CloudflareBypassException : Exception()
