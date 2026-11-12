// Ported from Mihon's data/download/DownloadProvider.kt (Apache-2.0), adapted to
// KodaStream's MangaEntry/MangaChapter models.
package com.kodastream.app.manga.download

import android.content.Context
import com.hippo.unifile.UniFile
import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Where a chapter is saved: /<root downloads dir>/<source name>/<manga>/<chapter>.
 * The source name is the one stored with the entry, so a downloaded chapter can
 * still be found after its extension has been removed.
 */
class MangaDownloadProvider(private val context: Context) {

    private val downloadsDir: UniFile?
        get() = MangaDownloadStorage.downloadsDirectory(context)

    /** Creates the source and manga folders for [manga]. */
    suspend fun getMangaDir(manga: MangaEntry): Result<UniFile> = withContext(Dispatchers.IO) {
        val root = downloadsDir
            ?: return@withContext Result.failure(IOException("Failed to create download directory"))
        val sourceDir = root.createDirectory(getSourceDirName(manga))
            ?: return@withContext Result.failure(IOException("Failed to create source download directory"))
        val mangaDir = sourceDir.createDirectory(getMangaDirName(manga.title))
            ?: return@withContext Result.failure(IOException("Failed to create manga download directory"))
        Result.success(mangaDir)
    }

    suspend fun findSourceDir(manga: MangaEntry): UniFile? = withContext(Dispatchers.IO) {
        downloadsDir?.findFile(getSourceDirName(manga))
    }

    suspend fun findMangaDir(manga: MangaEntry): UniFile? = withContext(Dispatchers.IO) {
        findSourceDir(manga)?.findFile(getMangaDirName(manga.title))
    }

    /** The chapter's folder or CBZ, whichever name it was saved under. */
    suspend fun findChapterDir(manga: MangaEntry, chapter: MangaChapter): UniFile? =
        withContext(Dispatchers.IO) {
            val mangaDir = findMangaDir(manga) ?: return@withContext null
            findChapterDirs(mangaDir, listOf(chapter)).values.firstOrNull()
        }

    /**
     * Finds every chapter of [chapters] that is on disk, keyed by chapter URL.
     * One directory listing is enough for all of them, and a content-resolver
     * listing ignores case just like a single lookup does.
     */
    suspend fun findDownloadedChapterDirs(
        manga: MangaEntry,
        chapters: List<MangaChapter>,
    ): Map<String, UniFile> = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext emptyMap()
        val mangaDir = findMangaDir(manga) ?: return@withContext emptyMap()
        findChapterDirs(mangaDir, chapters)
    }

    private fun findChapterDirs(
        mangaDir: UniFile,
        chapters: List<MangaChapter>,
    ): Map<String, UniFile> {
        val byName = mangaDir.listFiles().orEmpty()
            .filter { it.name != null }
            .associateByTo(java.util.TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.name!! }
        return chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter)
                .firstNotNullOfOrNull { byName[it] }
                ?.let { chapter.url to it }
        }.toMap()
    }

    fun getSourceDirName(manga: MangaEntry): String = sourceDirName(manga)

    fun getMangaDirName(mangaTitle: String): String = mangaDirName(mangaTitle)

    fun getChapterDirName(chapter: MangaChapter): String = chapterDirName(chapter)

    /** The folder and the archive name a chapter may have been saved under. */
    fun getValidChapterDirNames(chapter: MangaChapter): List<String> {
        val dir = getChapterDirName(chapter)
        return listOf(dir, "$dir.cbz")
    }

    suspend fun deleteChapter(manga: MangaEntry, chapter: MangaChapter) = withContext(Dispatchers.IO) {
        findChapterDir(manga, chapter)?.delete()
        // Drop the manga folder too when that was its last chapter.
        val mangaDir = findMangaDir(manga)
        if (mangaDir?.listFiles().isNullOrEmpty()) {
            mangaDir?.delete()
            val sourceDir = findSourceDir(manga)
            if (sourceDir?.listFiles().isNullOrEmpty()) sourceDir?.delete()
        }
    }

    suspend fun deleteManga(manga: MangaEntry) = withContext(Dispatchers.IO) {
        findSourceDir(manga)?.findFile(getMangaDirName(manga.title))?.delete()
        val sourceDir = findSourceDir(manga)
        if (sourceDir?.listFiles().isNullOrEmpty()) sourceDir?.delete()
    }

    companion object {
        /** The source folder, kept findable even after its extension is removed. */
        fun sourceDirName(manga: MangaEntry): String =
            MangaDiskUtil.buildValidFilename(
                manga.sourceName.ifBlank { "source-${manga.sourceId}" },
            )

        fun mangaDirName(mangaTitle: String): String =
            MangaDiskUtil.buildValidFilename(mangaTitle)

        /**
         * The folder name Mihon uses: "<scanlator>_<chapter name>", or just the
         * chapter name. Room is left for a ".cbz" suffix.
         */
        fun chapterDirName(chapter: MangaChapter): String {
            val scanlator = chapter.scanlator
            val name = if (scanlator.isNullOrBlank()) chapter.name else "${scanlator}_${chapter.name}"
            return MangaDiskUtil.buildValidFilename(
                name.ifBlank { "Chapter" },
                MangaDiskUtil.MAX_FILE_NAME_BYTES - 11,
            )
        }
    }
}
