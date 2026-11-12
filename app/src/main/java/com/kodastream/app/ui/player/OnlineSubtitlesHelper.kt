package com.kodastream.app.ui.player

import android.content.Context
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.zip.ZipInputStream

// SubDL API Key: replace with your personal key from https://subdl.com
// Leave empty to use the keyless OpenSubtitles public endpoint automatically.
const val SUBDL_API_KEY = ""

data class OnlineSubtitle(
    val name: String,
    val language: String,
    val url: String,
    val source: String = "Online",
)

object OnlineSubtitlesHelper {

    suspend fun searchSubtitles(
        imdbId: String?,
        title: String,
    ): List<OnlineSubtitle> = searchSubtitles(imdbId, title, null, null)

    suspend fun searchSubtitles(
        imdbId: String?,
        title: String,
        season: Int?,
        episode: Int?,
    ): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        // 1. If SubDL API key is configured, query SubDL first
        if (SUBDL_API_KEY.isNotBlank()) {
            try {
                val subdlResults = searchSubdl(imdbId, title, season, episode)
                if (subdlResults.isNotEmpty()) return@withContext subdlResults
            } catch (_: Throwable) {
                // Fall through to keyless endpoint on network/auth error
            }
        }

        // 2. Query keyless OpenSubtitles public endpoint
        try {
            searchOpenSubtitlesKeyless(imdbId, title, season, episode)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun searchSubdl(
        imdbId: String?,
        title: String,
        season: Int?,
        episode: Int?,
    ): List<OnlineSubtitle> {
        val cleanImdb = imdbId?.trim()?.let { if (it.startsWith("tt")) it else "tt$it" }
        val params = mutableListOf("api_key=$SUBDL_API_KEY")

        if (!cleanImdb.isNullOrBlank()) {
            params.add("imdb_id=$cleanImdb")
        } else if (title.isNotBlank()) {
            params.add("film_name=${URLEncoder.encode(title, "UTF-8")}")
        } else {
            return emptyList()
        }

        if (season != null && season > 0) {
            params.add("season_number=$season")
        }
        if (episode != null && episode > 0) {
            params.add("episode_number=$episode")
        }

        val url = "https://api.subdl.com/api/v1/subtitles?${params.joinToString("&")}"
        val response = app.get(url).text
        return parseSubdlResponse(response)
    }

    internal fun parseSubdlResponse(jsonStr: String): List<OnlineSubtitle> {
        val json = JSONObject(jsonStr)
        if (!json.optBoolean("status", false)) return emptyList()

        val subsArray = json.optJSONArray("subtitles") ?: return emptyList()
        val list = mutableListOf<OnlineSubtitle>()
        for (i in 0 until subsArray.length()) {
            val item = subsArray.getJSONObject(i)
            val subUrl = item.optString("url")
            if (subUrl.isBlank()) continue
            val fullUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"
            val releaseName = item.optString("release_name").ifBlank {
                item.optString("name").ifBlank {
                    item.optString("file_name", "Subtitle ${i + 1}")
                }
            }
            val lang = item.optString("language", "English")
            list.add(OnlineSubtitle(name = releaseName, language = lang, url = fullUrl, source = "SubDL"))
        }
        return list
    }

    private suspend fun searchOpenSubtitlesKeyless(
        imdbId: String?,
        title: String,
        season: Int?,
        episode: Int?,
    ): List<OnlineSubtitle> {
        var resolvedImdb = imdbId?.trim()?.let { if (it.startsWith("tt")) it else "tt$it" }

        // If no IMDb ID, resolve via Cinemeta catalog search
        if (resolvedImdb.isNullOrBlank() && title.isNotBlank()) {
            resolvedImdb = resolveImdbViaCinemeta(title)
        }

        if (resolvedImdb.isNullOrBlank()) return emptyList()

        val endpoint = if (season != null && season > 0 && episode != null && episode > 0) {
            "https://opensubtitles-v3.strem.io/subtitles/series/$resolvedImdb:$season:$episode.json"
        } else {
            "https://opensubtitles-v3.strem.io/subtitles/movie/$resolvedImdb.json"
        }

        val response = app.get(endpoint).text
        return parseOpenSubtitlesResponse(response)
    }

    private suspend fun resolveImdbViaCinemeta(title: String): String? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        try {
            val movieUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encoded.json"
            val movieRes = app.get(movieUrl).text
            val movieMetas = JSONObject(movieRes).optJSONArray("metas")
            if (movieMetas != null && movieMetas.length() > 0) {
                val imdb = movieMetas.getJSONObject(0).optString("imdb_id")
                if (imdb.isNotBlank()) return imdb
            }
        } catch (_: Throwable) {}

        try {
            val seriesUrl = "https://v3-cinemeta.strem.io/catalog/series/top/search=$encoded.json"
            val seriesRes = app.get(seriesUrl).text
            val seriesMetas = JSONObject(seriesRes).optJSONArray("metas")
            if (seriesMetas != null && seriesMetas.length() > 0) {
                val imdb = seriesMetas.getJSONObject(0).optString("imdb_id")
                if (imdb.isNotBlank()) return imdb
            }
        } catch (_: Throwable) {}

        return null
    }

    internal fun parseOpenSubtitlesResponse(jsonStr: String): List<OnlineSubtitle> {
        val json = JSONObject(jsonStr)
        val subsArray = json.optJSONArray("subtitles") ?: return emptyList()
        val list = mutableListOf<OnlineSubtitle>()
        for (i in 0 until subsArray.length()) {
            val item = subsArray.getJSONObject(i)
            val subUrl = item.optString("url")
            if (subUrl.isBlank()) continue
            val release = item.optString("movieReleaseName").ifBlank {
                item.optString("subtitleFileName", "Subtitle ${i + 1}")
            }
            val lang = item.optString("lang", "en")
            list.add(OnlineSubtitle(name = release, language = lang, url = subUrl, source = "OpenSubtitles"))
        }
        return list
    }

    suspend fun downloadSubtitle(subtitleUrl: String): File {
        val context = CloudStreamApp.context ?: error("CloudStreamApp.context is null")
        return downloadSubtitle(context, subtitleUrl)
    }

    suspend fun downloadSubtitle(context: Context, subtitleUrl: String): File = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val bytes = app.get(subtitleUrl).body.bytes()
        val timestamp = System.currentTimeMillis()

        // Check if payload is a ZIP archive
        val targetSrt = File(cacheDir, "online_sub_$timestamp.srt")
        if (extractSubtitleFromZip(bytes, targetSrt)) {
            return@withContext targetSrt
        }

        // Direct subtitle file (.srt or .vtt)
        val ext = if (subtitleUrl.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
        val targetFile = File(cacheDir, "online_sub_$timestamp.$ext")
        targetFile.writeBytes(bytes)
        targetFile
    }

    internal fun extractSubtitleFromZip(bytes: ByteArray, targetFile: File): Boolean {
        // Zip magic bytes: 0x50, 0x4B, 0x03, 0x04
        if (bytes.size < 4 ||
            bytes[0] != 0x50.toByte() ||
            bytes[1] != 0x4B.toByte() ||
            bytes[2] != 0x03.toByte() ||
            bytes[3] != 0x04.toByte()
        ) {
            return false
        }

        return try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.endsWith(".srt") || name.endsWith(".vtt")) {
                        FileOutputStream(targetFile).use { fos ->
                            zip.copyTo(fos)
                        }
                        return true
                    }
                    entry = zip.nextEntry
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }
}
