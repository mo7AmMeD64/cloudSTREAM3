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
 * The folder names are remembered per title, so a downloaded chapter can still
 * be found after the source or the title was renamed, or its extension removed.
 */
class MangaDownloadProvider(private val context: Context) {

    private val downloadsDir: UniFile?
        get() = MangaDownloadStorage.downloadsDirectory(context)

    /** The source and manga folders of a title that is on disk. */
    private class MangaDir(val sourceName: String, val name: String, val dir: UniFile)

    /** Creates the source and manga folders for [manga]. */
    suspend fun getMangaDir(manga: MangaEntry): Result<UniFile> = withContext(Dispatchers.IO) {
        val root = downloadsDir
            ?: return@withContext Result.failure(IOException("Failed to create download directory"))
        // Reuse the folder of an earlier download when the title or source was
        // renamed, the same way Mihon's download cache does.
        val stored = MangaDownloadFolderStore.get(context, manga)
        val sourceName = stored?.sourceDir?.ifBlank { null } ?: getSourceDirName(manga)
        val sourceDir = root.createDirectory(sourceName)
            ?: return@withContext Result.failure(IOException("Failed to create source download directory"))
        val mangaName = stored?.mangaDir?.ifBlank { null } ?: getMangaDirName(manga.title)
        val mangaDir = sourceDir.createDirectory(mangaName)
            ?: return@withContext Result.failure(IOException("Failed to create manga download directory"))
        remember(manga, sourceDir, mangaDir)
        Result.success(mangaDir)
    }

    suspend fun findSourceDir(manga: MangaEntry): UniFile? = withContext(Dispatchers.IO) {
        val root = downloadsDir ?: return@withContext null
        val stored = MangaDownloadFolderStore.get(context, manga)?.sourceDir
        sequenceOf(stored, getSourceDirName(manga))
            .filterNotNull()
            .distinct()
            .firstNotNullOfOrNull { root.findFile(it) }
    }

    suspend fun findMangaDir(manga: MangaEntry): UniFile? = withContext(Dispatchers.IO) {
        resolveMangaDir(manga)?.dir
    }

    /** The chapter's folder or CBZ, whichever name it was saved under. */
    suspend fun findChapterDir(manga: MangaEntry, chapter: MangaChapter): UniFile? =
        withContext(Dispatchers.IO) {
            val mangaDir = resolveMangaDir(manga)?.dir ?: return@withContext null
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
        val mangaDir = resolveMangaDir(manga)?.dir ?: return@withContext emptyMap()
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

    /**
     * Resolves the folders of an already downloaded title. The remembered
     * names are tried first, then the current names, then every source folder:
     * the source folder is named after the extension, which may be gone, and
     * the title may have been renamed by the user or the source.
     */
    private fun resolveMangaDir(manga: MangaEntry): MangaDir? {
        val root = downloadsDir ?: return null
        val stored = MangaDownloadFolderStore.get(context, manga)
        val sourceNames = listOfNotNull(stored?.sourceDir, getSourceDirName(manga)).distinct()
        val mangaNames = listOfNotNull(stored?.mangaDir, getMangaDirName(manga.title)).distinct()

        for (sourceName in sourceNames) {
            val sourceDir = root.findFile(sourceName) ?: continue
            for (mangaName in mangaNames) {
                val dir = sourceDir.findFile(mangaName)
                if (dir != null && dir.isDirectory) {
                    remember(manga, sourceDir, dir)
                    return MangaDir(sourceName, mangaName, dir)
                }
            }
        }

        // A scan by title also covers a source whose display name changed and a
        // source that is no longer installed.
        val sourceDirs = root.listFiles().orEmpty().filter { it.isDirectory }
        for (sourceDir in sourceDirs) {
            val sourceName = sourceDir.name ?: continue
            for (mangaName in mangaNames) {
                val dir = sourceDir.findFile(mangaName)
                if (dir != null && dir.isDirectory) {
                    remember(manga, sourceDir, dir)
                    return MangaDir(sourceName, mangaName, dir)
                }
            }
        }
        return null
    }

    private fun remember(manga: MangaEntry, sourceDir: UniFile, mangaDir: UniFile) {
        MangaDownloadFolderStore.remember(
            context,
            manga,
            sourceDir.name ?: getSourceDirName(manga),
            mangaDir.name ?: getMangaDirName(manga.title),
        )
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
        val resolved = resolveMangaDir(manga) ?: return@withContext
        val chapterDir = findChapterDirs(resolved.dir, listOf(chapter)).values.firstOrNull()
        chapterDir?.delete()
        // Drop the manga folder too when that was its last chapter.
        if (resolved.dir.listFiles().isNullOrEmpty()) {
            resolved.dir.delete()
            val sourceDir = downloadsDir?.findFile(resolved.sourceName)
            if (sourceDir?.listFiles().isNullOrEmpty()) sourceDir?.delete()
            MangaDownloadFolderStore.forget(context, manga)
        }
    }

    suspend fun deleteManga(manga: MangaEntry) = withContext(Dispatchers.IO) {
        val resolved = resolveMangaDir(manga) ?: return@withContext
        resolved.dir.delete()
        val sourceDir = downloadsDir?.findFile(resolved.sourceName)
        if (sourceDir?.listFiles().isNullOrEmpty()) sourceDir?.delete()
        MangaDownloadFolderStore.forget(context, manga)
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
