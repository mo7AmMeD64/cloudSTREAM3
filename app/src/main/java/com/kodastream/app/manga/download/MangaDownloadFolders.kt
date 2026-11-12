// Persisted folder names for a manga's downloads. Mihon keeps the same mapping
// in its download cache; KodaStream needs it because the extension may be
// removed or rename the source/title after the chapters were downloaded.
package com.kodastream.app.manga.download

import android.content.Context
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.key
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

data class MangaDownloadFolders(
    val sourceDir: String = "",
    val mangaDir: String = "",
)

internal object MangaDownloadFolderStore {
    private const val FOLDER = "manga_download_folders"

    fun get(context: Context, manga: MangaEntry): MangaDownloadFolders? =
        context.getKey<MangaDownloadFolders>(FOLDER, manga.key())
            ?.takeIf { it.sourceDir.isNotBlank() && it.mangaDir.isNotBlank() }

    fun remember(context: Context, manga: MangaEntry, sourceDir: String, mangaDir: String) {
        if (sourceDir.isBlank() || mangaDir.isBlank()) return
        context.setKey(FOLDER, manga.key(), MangaDownloadFolders(sourceDir, mangaDir))
    }

    fun forget(context: Context, manga: MangaEntry) {
        context.removeKey(FOLDER, manga.key())
    }
}
