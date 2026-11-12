// Ported from Mihon's reader/loader/DownloadPageLoader.kt (Apache-2.0), reading
// both the folder and the CBZ layouts through UniFile.
package com.kodastream.app.manga.reader

import android.content.Context
import com.hippo.unifile.UniFile
import com.kodastream.app.manga.download.MangaDiskUtil
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Loads a chapter from `downloads/<source>/<manga>/<chapter>` (a folder of
 * images) or from `<chapter>.cbz`. Pages are exposed as cache files because
 * the webtoon viewer requires a real [File].
 */
class MangaDownloadedPageLoader(
    private val context: Context,
    private val chapter: ReaderChapter,
    private val chapterDir: UniFile,
) : PageLoader() {

    override var isLocal: Boolean = true

    private var archive: ZipFile? = null

    private val pagesDir: File
        get() = File(context.cacheDir, "manga_downloads/${MangaDiskUtil.md5(chapterDir.uri.toString())}")
            .apply { mkdirs() }

    override suspend fun getPages(): List<ReaderPage> = withContext(Dispatchers.IO) {
        if (chapterDir.isFile) pagesFromArchive() else pagesFromDirectory()
    }

    private fun pagesFromDirectory(): List<ReaderPage> {
        val direct = chapterDir.filePath?.let(::File)?.takeIf { it.isDirectory }
        return chapterDir.listFiles().orEmpty()
            .filter { MangaDiskUtil.isImageName(it.name) }
            .sortedBy { it.name }
            .mapIndexed { index, entry ->
                ReaderPage(index, entry.name.orEmpty()).apply {
                    if (direct != null) {
                        val local = File(direct, entry.name.orEmpty())
                        this.file = local
                        stream = { local.inputStream() }
                        status = Page.State.Ready
                    }
                }
            }
    }

    private fun pagesFromArchive(): List<ReaderPage> {
        val zip = openArchive() ?: return emptyList()
        return zip.entries().asSequence()
            .filter { !it.isDirectory && MangaDiskUtil.isImageName(it.name) }
            .sortedBy { it.name }
            .mapIndexed { index, entry -> ReaderPage(index, entry.name) }
            .toList()
    }

    override suspend fun loadPage(page: ReaderPage) = withContext(Dispatchers.IO) {
        if (page.file?.isFile == true) return@withContext
        val local = when {
            chapterDir.isFile -> extractArchiveEntry(page)
            else -> extractDirectoryPage(page)
        } ?: throw IllegalStateException("The downloaded page is missing")
        page.file = local
        page.stream = { local.inputStream() }
        page.progress = 100
        page.status = Page.State.Ready
    }

    private fun extractDirectoryPage(page: ReaderPage): File? {
        val cached = File(pagesDir, page.url.replace('/', '_'))
        if (cached.isFile && cached.length() > 0L) return cached
        val source = chapterDir.findFile(page.url) ?: return null
        source.openInputStream().use { input ->
            cached.outputStream().use { input.copyTo(it) }
        }
        return cached.takeIf { it.isFile && it.length() > 0L }
    }

    private fun extractArchiveEntry(page: ReaderPage): File? {
        val zip = openArchive() ?: return null
        val cached = File(pagesDir, page.url.replace('/', '_'))
        if (cached.isFile && cached.length() > 0L) return cached
        val entry = zip.getEntry(page.url) ?: return null
        zip.getInputStream(entry).use { input ->
            cached.outputStream().use { input.copyTo(it) }
        }
        return cached.takeIf { it.isFile && it.length() > 0L }
    }

    private fun openArchive(): ZipFile? {
        archive?.let { return it }
        val direct = chapterDir.filePath?.let(::File)?.takeIf { it.isFile }
        val file = direct ?: run {
            val cached = File(context.cacheDir, "manga_downloads/${MangaDiskUtil.md5(chapterDir.uri.toString())}.cbz")
            if (!cached.isFile || cached.length() == 0L) {
                chapterDir.openInputStream().use { input ->
                    cached.outputStream().use { input.copyTo(it) }
                }
            }
            cached.takeIf { it.isFile && it.length() > 0L }
        } ?: return null
        return runCatching { ZipFile(file) }.getOrNull()?.also { archive = it }
    }

    override fun recycle() {
        super.recycle()
        archive?.close()
        archive = null
    }
}
