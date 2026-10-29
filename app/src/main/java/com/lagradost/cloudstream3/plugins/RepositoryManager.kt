package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A repository the user added (the url of its repo.json). */
data class RepositoryData(
    val name: String,
    val url: String,
    val iconUrl: String? = null,
)

/** The content of a repo.json file. */
data class Repository(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val manifestVersion: Int,
    val pluginLists: List<String>,
)

/**
 * One entry of a repository's plugin list.
 *
 * status: 0 = Down, 1 = Ok, 2 = Slow, 3 = Beta only
 */
data class SitePlugin(
    val url: String,
    val status: Int,
    val version: Int,
    val name: String,
    val internalName: String,
    val authors: List<String>,
    val description: String?,
    val language: String?,
    val tvTypes: List<String>?,
    val iconUrl: String?,
    val fileSize: Long?,
    val fileHash: String?,
)

/** A plugin that was downloaded to this device. */
data class InstalledPlugin(
    val internalName: String,
    val name: String,
    val repoUrl: String,
    val downloadUrl: String,
    val version: Int,
    val filePath: String,
    val iconUrl: String?,
)

private fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.strList(key: String): List<String>? {
    if (!has(key) || isNull(key)) return null
    val arr = optJSONArray(key) ?: return null
    return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
}

/**
 * Talks to extension repositories: parsing repo urls, reading repo.json and
 * plugins.json, and downloading .cs3 files. Same formats as CloudStream, so any
 * existing CloudStream repository works unchanged.
 */
object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"

    internal suspend fun httpGetString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        app.baseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} for $url")
            response.body?.string() ?: throw java.io.IOException("Empty body for $url")
        }
    }

    /** Returns a SHA-256 string of the file content, like "sha256-b70462...". */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Resolves a short link (like https://t.ly/xxxx, or a short code) to the final url without downloading it. */
    private suspend fun resolveRedirect(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = app.baseClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                response.header("Location")
            }
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    /**
     * Accepts what people actually paste: a direct https url, a
     * `cloudstreamrepo://` link, or a short code (cutt.ly, or py.md with a "!" prefix).
     */
    suspend fun parseRepoUrl(url: String): String? {
        val fixedUrl = url.trim()
        val schemeRegex = "^(cloudstreamrepo://)|(https://cs\\.repo/\\??)".toRegex()
        return when {
            fixedUrl.contains("^https?://".toRegex()) -> fixedUrl
            fixedUrl.contains(schemeRegex) -> {
                val stripped = fixedUrl.replace(schemeRegex, "")
                if (stripped.contains("^https?://".toRegex())) stripped else "https://$stripped"
            }
            fixedUrl.matches("^[a-zA-Z0-9!_-]+$".toRegex()) -> {
                if (fixedUrl.startsWith("!")) {
                    val location = resolveRedirect("https://py.md/${fixedUrl.removePrefix("!")}") ?: return null
                    if (location.startsWith("https://py.md/404") || location.removeSuffix("/") == "https://py.md") null else location
                } else {
                    val location = resolveRedirect("https://cutt.ly/$fixedUrl") ?: return null
                    if (location.startsWith("https://cutt.ly/404") || location.removeSuffix("/") == "https://cutt.ly") null else location
                }
            }
            else -> null
        }
    }

    suspend fun parseRepository(url: String): Repository? {
        return try {
            val json = JSONObject(httpGetString(url))
            val lists = json.optJSONArray("pluginLists") ?: JSONArray()
            Repository(
                name = json.str("name") ?: return null,
                description = json.str("description"),
                iconUrl = json.str("iconUrl"),
                manifestVersion = json.optInt("manifestVersion", 1),
                pluginLists = (0 until lists.length()).mapNotNull { i -> lists.optString(i).takeIf { it.isNotBlank() } },
            )
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    private suspend fun parsePlugins(pluginsUrl: String): List<SitePlugin> {
        return try {
            val array = JSONArray(httpGetString(pluginsUrl))
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                SitePlugin(
                    url = obj.str("url") ?: return@mapNotNull null,
                    status = obj.optInt("status", 1),
                    version = obj.optInt("version", 1),
                    name = obj.str("name") ?: obj.str("internalName") ?: return@mapNotNull null,
                    internalName = obj.str("internalName") ?: obj.str("name") ?: return@mapNotNull null,
                    authors = obj.strList("authors") ?: emptyList(),
                    description = obj.str("description"),
                    language = obj.str("language"),
                    tvTypes = obj.strList("tvTypes"),
                    iconUrl = obj.str("iconUrl"),
                    fileSize = if (obj.has("fileSize") && !obj.isNull("fileSize")) obj.optLong("fileSize") else null,
                    fileHash = obj.str("fileHash"),
                )
            }
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
    }

    /** All plugins offered by a repository, or null when the repo.json cannot be read. */
    suspend fun getRepoPlugins(repo: RepositoryData): List<SitePlugin>? {
        val parsed = parseRepository(repo.url) ?: return null
        return parsed.pluginLists.amap { listUrl -> parsePlugins(listUrl) }.flatten()
    }

    /** Downloads a .cs3 to [file]. Verifies the hash when the repository provides one. Returns an error message or null. */
    suspend fun downloadPluginToFile(
        context: Context,
        pluginUrl: String,
        file: File,
        expectedFileHash: String?,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val parentDir = file.parentFile ?: return@withContext "No parent folder"
            parentDir.mkdirs()

            // Download to a temp file first so a failed download never corrupts a working plugin.
            val tempFile = File.createTempFile("plugin", ".tmp", context.cacheDir)
            val request = Request.Builder().url(pluginUrl).header("User-Agent", USER_AGENT).build()
            app.baseClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    tempFile.delete()
                    return@withContext "HTTP ${response.code}"
                }
                val body = response.body ?: run {
                    tempFile.delete()
                    return@withContext "Empty download"
                }
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            if (expectedFileHash != null) {
                val actual = sha256(tempFile)
                if (actual != expectedFileHash) {
                    tempFile.delete()
                    return@withContext "Hash mismatch (expected $expectedFileHash, got $actual)"
                }
            }

            // A previous copy may be read-only (it was loaded as a dex file).
            if (file.exists()) {
                file.setWritable(true)
                file.delete()
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            null
        } catch (t: Throwable) {
            logError(t)
            t.message ?: t.javaClass.simpleName
        }
    }
}
