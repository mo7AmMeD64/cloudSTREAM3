package com.kodastream.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.downloads.DownloadMigration
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.engine.AppBootstrap
import com.kodastream.app.ui.AppRoot
import com.kodastream.app.ui.setup.SetupWizard
import com.kodastream.app.ui.theme.KodaStreamTheme
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel
import com.kodastream.app.ui.update.UpdateDialog
import com.kodastream.app.ui.update.UpdateViewModel
import com.lagradost.cloudstream3.CommonActivity

/**
 * AppCompatActivity rather than ComponentActivity on purpose: extensions
 * written for CloudStream sometimes cast the current activity to AppCompatActivity.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The window theme and AppCompat dialogs follow the in-app mode, not
        // the system one. Must happen before super.onCreate.
        ThemeViewModel.applyNightMode(this)
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (night) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        CommonActivity.setActivityInstance(this)
        installImageLoader()
        AppBootstrap.start(this)
        handleIntent(intent)

        // One-time: older downloads move into their title folders.
        lifecycleScope.launch(Dispatchers.IO) { DownloadMigration.run(this@MainActivity) }

        // Interrupted and queued downloads resume where they left off.
        if (DownloadStore.current(this).any {
                it.state == DownloadStore.STATE_QUEUED || it.state == DownloadStore.STATE_DOWNLOADING
            }
        ) {
            DownloadService.ensure(this)
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            // Activity-scoped: once per process, and survives recreation.
            val updateViewModel: UpdateViewModel = viewModel()
            var setupDone by remember { mutableStateOf(AppPrefs.setupDone(this@MainActivity)) }
            val update = updateViewModel.update
            val dark = when (themeViewModel.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KodaStreamTheme(
                darkTheme = dark,
                colorPalette = themeViewModel.palette,
                amoledDark = themeViewModel.amoled,
                paletteStyle = themeViewModel.style,
            ) {
                if (setupDone) {
                    AppRoot(themeViewModel)
                    update?.let { available ->
                        UpdateDialog(
                            update = available,
                            currentVersion = BuildConfig.VERSION_NAME,
                            onUpdate = {
                                if (openLink(available.url)) {
                                    updateViewModel.dismiss()
                                } else {
                                    CommonActivity.showToast(R.string.update_link_failed)
                                }
                            },
                            onDonate = {
                                if (!openLink(DONATE_URL)) {
                                    CommonActivity.showToast(R.string.update_link_failed)
                                }
                            },
                            onLater = updateViewModel::dismiss,
                            onDontShowAgain = updateViewModel::skip,
                        )
                    }
                } else {
                    SetupWizard(themeViewModel) { language ->
                        AppPrefs.setSetupDone(this@MainActivity, true)
                        AppPrefs.setAppLanguage(this@MainActivity, language)
                        AppPrefs.applyAppLanguage(this@MainActivity)
                        setupDone = true
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }

    private fun handleIntent(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (link.startsWith("cloudstreamrepo://") || link.startsWith("https://cs.repo")) {
            AppEvents.pendingRepoUrl.value = link
        }
    }

    /** Opens an https link in the browser; false when nothing can handle it. */
    private fun openLink(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
    }

    /**
     * One image loader for the whole process. Provider posters rarely send
     * useful cache headers, so they are cached by URL instead of being
     * re-fetched on every shelf; the crossfade lives here so individual
     * requests stay identical and Coil can skip it on memory-cache hits.
     */
    private fun installImageLoader() {
        if (imageLoaderInstalled) return
        imageLoaderInstalled = true
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(150)
                .build()
        )
    }

    private companion object {
        const val DONATE_URL = "https://linktr.ee/x3onq"
        var imageLoaderInstalled = false
    }
}
