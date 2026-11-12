package com.kodastream.app.manga.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kodastream.app.R
import com.kodastream.app.manga.extension.MangaSourceManager
import eu.kanade.tachiyomi.compat.MangaEngine
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The in-app browser for a source that needs a Cloudflare check, like Mihon's.
 *
 * Cloudflare ties its `cf_clearance` cookie to the User-Agent that earned it, so
 * this page presents the same User-Agent and headers as the source's own client.
 * With any other agent the check would pass here and still be refused on the
 * next request. Cookies land in the CookieManager that the source client reads;
 * the caller gets RESULT_OK back and reloads.
 */
class MangaWebViewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val CLEARANCE = "cf_clearance"

        fun intent(context: Context, url: String, sourceId: Long = 0L): Intent =
            Intent(context, MangaWebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SOURCE_ID, sourceId)
    }

    private var webView: WebView? = null
    private var announcedSolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val source = MangaSourceManager.get(this)
            .sourceById(intent.getLongExtra(EXTRA_SOURCE_ID, 0L)) as? HttpSource
        val userAgent = source?.headers?.get("User-Agent")?.takeIf { it.isNotBlank() }
            ?: MangaEngine.get(this).network?.defaultUserAgentProvider()?.takeIf { it.isNotBlank() }
        val initialClearance = clearance(url)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            visibility = View.GONE
        }
        val title = TextView(this).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = android.net.Uri.parse(url).host.orEmpty()
        }
        val web = WebView(this).also { webView = it }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setDefaultSettings()
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            if (userAgent != null) setUserAgent(userAgent)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }

                override fun onReceivedTitle(view: WebView?, pageTitle: String?) {
                    if (!pageTitle.isNullOrBlank()) title.text = pageTitle
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                    progress.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    progress.visibility = View.GONE
                    val now = clearance(pageUrl ?: url)
                    if (!announcedSolved && !now.isNullOrBlank() && now != initialClearance) {
                        announcedSolved = true
                        CookieManager.getInstance().flush()
                        Toast.makeText(
                            this@MangaWebViewActivity,
                            R.string.manga_webview_solved,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            addView(
                ImageButton(this@MangaWebViewActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setBackgroundColor(0)
                    contentDescription = getString(R.string.back)
                    setOnClickListener { finishWithResult() }
                },
                LinearLayout.LayoutParams(dp(48), dp(48)),
            )
            addView(
                title,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageButton(this@MangaWebViewActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_rotate)
                    setBackgroundColor(0)
                    contentDescription = getString(R.string.manga_refresh)
                    setOnClickListener { web.reload() }
                },
                LinearLayout.LayoutParams(dp(48), dp(48)),
            )
            addView(
                ImageButton(this@MangaWebViewActivity).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    setBackgroundColor(0)
                    contentDescription = getString(R.string.settings_manga_clear_cookies)
                    setOnClickListener {
                        val current = (web.url ?: url).toHttpUrlOrNull()
                        if (current != null) {
                            MangaEngine.get(this@MangaWebViewActivity).network?.cookieJar?.remove(current)
                            Toast.makeText(
                                this@MangaWebViewActivity,
                                R.string.settings_manga_cookies_cleared,
                                Toast.LENGTH_SHORT,
                            ).show()
                            web.reload()
                        }
                    }
                },
                LinearLayout.LayoutParams(dp(48), dp(48)),
            )
            addView(
                Button(this@MangaWebViewActivity).apply {
                    text = getString(R.string.manga_webview_done)
                    setOnClickListener { finishWithResult() }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val background = TypedValue().also {
            theme.resolveAttribute(android.R.attr.colorBackground, it, true)
        }.data
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            addView(
                progress,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)),
            )
            addView(web)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(background)
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        // Edge-to-edge: keep the bar and the page clear of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finishWithResult()
        }

        val headers = source?.headers?.toWebViewHeaders().orEmpty()
        web.loadUrl(url, headers)
    }

    private fun clearance(url: String): String? =
        CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$CLEARANCE=") }
            ?.substringAfter('=')

    private fun finishWithResult() {
        CookieManager.getInstance().flush()
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
