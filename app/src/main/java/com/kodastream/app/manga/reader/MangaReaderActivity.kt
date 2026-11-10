package com.kodastream.app.manga.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kodastream.app.R
import com.kodastream.app.data.NavArgs
import com.kodastream.app.manga.MangaChapterStore
import com.kodastream.app.manga.MangaReaderOrientation
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.MangaReaderTheme
import com.kodastream.app.manga.MangaReadingMode
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.TapAction
import com.kodastream.app.manga.tapAction
import com.kodastream.app.ui.theme.KodaStreamTheme
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLConnection

/**
 * Hosts the ported View-based reader. The system bars stay hidden, the viewer
 * fills the cutout area, and the only Compose is the overlay menu and settings.
 */
class MangaReaderActivity : AppCompatActivity(), ReaderViewerHost {

    companion object {
        private const val EXTRA_REF = "manga_ref"
        private const val EXTRA_CHAPTER = "chapter_url"
        private const val EXTRA_PAGE = "page"

        fun intent(
            context: Context,
            ref: MangaRef,
            chapterUrl: String,
            page: Int = 0,
        ): Intent = Intent(context, MangaReaderActivity::class.java)
            .putExtra(EXTRA_REF, NavArgs.put(ref))
            .putExtra(EXTRA_CHAPTER, chapterUrl)
            .putExtra(EXTRA_PAGE, page)
    }

    private lateinit var ref: MangaRef
    private lateinit var readerModel: MangaReaderViewModel
    private lateinit var viewerHost: FrameLayout
    private lateinit var readerRoot: FrameLayout

    @Volatile
    override var settings: MangaReaderSettings = MangaReaderSettings()
        private set

    override val sourceId: Long get() = ref.sourceId
    override val readerScope: CoroutineScope get() = lifecycleScope

    private var viewer: ReaderViewer? = null
    private var currentChapters: ViewerChapters? = null
    private var currentPage: ReaderPage? = null
    @Volatile
    private var sliderDragging = false
    private val overlayState = mutableStateOf(ReaderOverlayState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val refId = intent.getIntExtra(EXTRA_REF, -1)
        val loadedRef = NavArgs.manga(refId)
        if (loadedRef == null) {
            // Process death clears NavArgs; the caller goes back to details.
            finish()
            return
        }
        ref = loadedRef
        val chapterUrl = intent.getStringExtra(EXTRA_CHAPTER).orEmpty()
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        if (chapterUrl.isBlank()) {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        readerModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MangaReaderViewModel(application, ref, chapterUrl, page) as T
            },
        )[MangaReaderViewModel::class.java]

        settings = readerModel.overlay.value.settings
        overlayState.value = readerModel.overlay.value

        viewerHost = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(readerBackground(settings.theme))
            addView(viewerHost)
            addView(
                ComposeView(this@MangaReaderActivity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        val state = overlayState.value
                        var diagnosticsText by remember { mutableStateOf<String?>(null) }
                        KodaStreamTheme(
                            darkTheme = when (state.settings.theme) {
                                MangaReaderTheme.WHITE -> false
                                MangaReaderTheme.AUTO -> (resources.configuration.uiMode and
                                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                                else -> true
                            },
                            amoledDark = state.settings.theme == MangaReaderTheme.BLACK,
                        ) {
                            MangaReaderOverlay(
                                state = state,
                                onClose = ::finishReader,
                                onModeChange = readerModel::setReadingMode,
                                onSettings = readerModel::updateSettings,
                                onPageSelected = ::moveToPage,
                                onPreviousChapter = { openAdjacent(-1) },
                                onNextChapter = { openAdjacent(1) },
                                onSliderDragging = { sliderDragging = it },
                                onBookmark = ::toggleBookmark,
                                onOpenBrowser = ::openInBrowser,
                                onSharePage = { sharePage(share = true) },
                                onSavePage = { sharePage(share = false) },
                                onRetry = readerModel::retry,
                                onOpenWebView = ::openInWebView,
                                onChapterSelected = readerModel::openChapter,
                                onCloseMenu = { readerModel.setMenuVisible(false) },
                                onShowDiagnostics = {
                                    diagnosticsText = buildDiagnosticsText()
                                },
                            )
                            diagnosticsText?.let { text ->
                                ReaderDiagnosticsDialog(
                                    text = text,
                                    onDismiss = { diagnosticsText = null },
                                )
                            }
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        readerRoot = root
        setContentView(root)
        applySettings(settings)
        hideSystemBars()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    readerModel.overlay.collect { state ->
                        overlayState.value = state
                        applySettings(state.settings)
                    }
                }
                launch {
                    readerModel.viewer.collect { state ->
                        val chapters = state.chapters ?: return@collect
                        currentChapters = chapters
                        if (viewer == null) createViewer()
                        viewer?.setChapters(chapters)
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (overlayState.value.menuVisible) {
                readerModel.setMenuVisible(false)
            } else {
                finishReader()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        if (::readerModel.isInitialized) readerModel.saveProgressNow()
        super.onPause()
    }

    override fun onDestroy() {
        viewer?.destroy()
        viewer = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (overlayState.value.menuVisible) return super.onKeyDown(keyCode, event)
        if (settings.volumeKeys) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    viewer?.moveForward()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    viewer?.moveBackward()
                    return true
                }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_SPACE,
            -> {
                viewer?.moveForward()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> {
                viewer?.moveBackward()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPageSelected(page: ReaderPage) {
        currentPage = page
        readerModel.onPageSelected(page)
        // Mihon closes the bars on a turn; a slider drag keeps them open.
        if (overlayState.value.menuVisible && !sliderDragging) {
            readerModel.setMenuVisible(false)
        }
    }

    override fun onTransitionSelected(transition: ChapterTransition) = Unit

    override fun showMenu() {
        if (!overlayState.value.menuVisible) readerModel.setMenuVisible(true)
    }

    override fun hideMenu() {
        if (overlayState.value.menuVisible) readerModel.setMenuVisible(false)
    }

    override fun onTap(xFraction: Float, yFraction: Float) {
        val action = tapAction(
            x = xFraction,
            y = yFraction,
            layout = settings.tapLayout,
            invert = settings.tapInvert,
            readingRtl = settings.mode.readingRtl,
        )
        when (action) {
            TapAction.MENU -> readerModel.toggleMenu()
            TapAction.FORWARD -> viewer?.moveForward()
            TapAction.BACKWARD -> viewer?.moveBackward()
        }
    }

    override fun onLongTap(page: ReaderPage) {
        if (settings.longTapSave) {
            currentPage = page
            sharePage(share = false)
        }
    }

    override fun requestPreloadChapter(chapter: ReaderChapter) {
        readerModel.requestPreloadChapter(chapter)
    }

    override fun reloadChapter(chapter: ReaderChapter) {
        readerModel.reloadChapter(chapter)
    }

    override fun openInWebView() {
        val url = readerModel.chapterWebUrl() ?: readerModel.mangaWebUrl() ?: return
        startActivity(MangaWebViewActivity.intent(this, url))
    }

    private fun createViewer() {
        val mode = settings.mode
        val created: ReaderViewer = if (mode.isPaged) {
            PagerViewer(
                context = this,
                host = this,
                readingRtl = mode.readingRtl,
                isHorizontal = mode != MangaReadingMode.PAGED_VERTICAL,
            )
        } else {
            WebtoonViewer(this, this)
        }
        viewer = created
        viewerHost.addView(created.view)
    }

    private fun recreateViewer() {
        val chapters = currentChapters
        val stillThere = currentPage?.takeIf { chapters?.currChapter?.pages?.contains(it) == true }
        val index = stillThere?.index ?: overlayState.value.page
        viewer?.destroy()
        viewer = null
        viewerHost.removeAllViews()
        if (chapters == null) return
        createViewer()
        viewer?.setChapters(chapters)
        val target = stillThere ?: chapters.currChapter.pages?.getOrNull(index)
        if (target != null) viewer?.moveToPage(target)
    }

    private fun applySettings(next: MangaReaderSettings) {
        val previous = settings
        settings = next
        window.setFlags(
            if (next.keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        requestedOrientation = when (next.orientation) {
            MangaReaderOrientation.FREE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            MangaReaderOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            MangaReaderOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        window.decorView.setBackgroundColor(readerBackground(next.theme))
        if (::readerRoot.isInitialized) readerRoot.setBackgroundColor(readerBackground(next.theme))
        if (previous != next && (previous.mode != next.mode ||
                previous.scaleType != next.scaleType ||
                previous.zoomStart != next.zoomStart ||
                previous.cropBorders != next.cropBorders ||
                previous.pageTransitions != next.pageTransitions ||
                previous.webtoonPadding != next.webtoonPadding ||
                previous.doubleTapZoom != next.doubleTapZoom)
        ) {
            recreateViewer()
        }
    }

    private fun readerBackground(theme: MangaReaderTheme): Int = when (theme) {
        MangaReaderTheme.BLACK -> Color.BLACK
        MangaReaderTheme.WHITE -> Color.WHITE
        MangaReaderTheme.GRAY -> Color.rgb(32, 32, 32)
        MangaReaderTheme.AUTO -> if ((resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun moveToPage(index: Int) {
        val pages = currentChapters?.currChapter?.pages ?: return
        val page = pages.getOrNull(index) ?: return
        viewer?.moveToPage(page)
    }

    private fun openAdjacent(delta: Int) {
        lifecycleScope.launch {
            readerModel.openAdjacent(delta)
        }
    }

    private fun toggleBookmark() {
        val chapter = currentPage?.chapter?.chapter ?: readerModel.currentChapter() ?: return
        readerModel.toggleBookmark(chapter)
    }

    private fun openInBrowser() {
        val url = readerModel.chapterWebUrl() ?: readerModel.mangaWebUrl() ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun buildDiagnosticsText(): String {
        val chapter = currentChapters?.currChapter
        val pages = chapter?.pages.orEmpty().map { page ->
            ReaderDiagnosticPage(
                index = page.index,
                state = pageStateLabel(page.status),
                progress = page.progress,
                host = page.imageUrl?.let { url -> runCatching { Uri.parse(url).host }.getOrNull() },
                error = (page.status as? Page.State.Error)?.error?.message,
            )
        }
        return readerDiagnosticsText(
            mangaTitle = ref.title,
            chapterName = chapter?.chapter?.name.orEmpty(),
            pages = pages,
            events = ReaderDiagnostics.recent(80).map { it.asLine() },
        )
    }

    private fun sharePage(share: Boolean) {
        val page = currentPage ?: return
        val file = page.file ?: return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (share) {
                        val uri = FileProvider.getUriForFile(
                            this@MangaReaderActivity,
                            "$packageName.fileprovider",
                            file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType(file)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.manga_reader_share_page)))
                    } else {
                        saveToPictures(file)
                    }
                }
            }
            result.onSuccess {
                if (!share) toast(getString(R.string.manga_reader_page_saved))
            }.onFailure {
                toast(
                    getString(
                        if (share) R.string.manga_reader_page_share_failed
                        else R.string.manga_reader_page_save_failed,
                    ),
                )
            }
        }
    }

    /** MediaStore on Android 10+, app Pictures folder on older devices. */
    private fun saveToPictures(file: File) {
        val name = "manga_${System.currentTimeMillis()}.${file.extension.ifBlank { "jpg" }}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType(file))
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/KodaStream",
                )
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore refused the image")
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("MediaStore refused the image stream")
        } else {
            @Suppress("DEPRECATION")
            val pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: error("No external storage")
            val folder = File(pictures, "KodaStream").apply { mkdirs() }
            file.copyTo(File(folder, name), overwrite = true)
        }
    }

    private fun mimeType(file: File): String =
        URLConnection.guessContentTypeFromName(file.name) ?: "image/jpeg"

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun finishReader() {
        readerModel.saveProgressNow()
        finish()
    }
}
