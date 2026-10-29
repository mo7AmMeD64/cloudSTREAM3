package com.lagradost.cloudstream3.plugins

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's repositories and the plugins installed from them.
 * Small and explicit JSON on purpose: this data must survive app updates.
 */
internal object ExtensionStore {
    private const val PREFS = "kodastream_extensions"
    private const val KEY_REPOS = "repositories"
    private const val KEY_PLUGINS = "installed_plugins"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    fun repositories(context: Context): List<RepositoryData> {
        val raw = prefs(context).getString(KEY_REPOS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                RepositoryData(
                    name = o.str("name") ?: return@mapNotNull null,
                    url = o.str("url") ?: return@mapNotNull null,
                    iconUrl = o.str("iconUrl"),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveRepositories(context: Context, repos: List<RepositoryData>) {
        val array = JSONArray()
        repos.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("name", r.name)
                    put("url", r.url)
                    if (r.iconUrl != null) put("iconUrl", r.iconUrl)
                }
            )
        }
        prefs(context).edit { putString(KEY_REPOS, array.toString()) }
    }

    fun installed(context: Context): List<InstalledPlugin> {
        val raw = prefs(context).getString(KEY_PLUGINS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                InstalledPlugin(
                    internalName = o.str("internalName") ?: return@mapNotNull null,
                    name = o.str("name") ?: o.str("internalName") ?: return@mapNotNull null,
                    repoUrl = o.str("repoUrl") ?: "",
                    downloadUrl = o.str("downloadUrl") ?: "",
                    version = o.optInt("version", 1),
                    filePath = o.str("filePath") ?: return@mapNotNull null,
                    iconUrl = o.str("iconUrl"),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveInstalled(context: Context, plugins: List<InstalledPlugin>) {
        val array = JSONArray()
        plugins.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("internalName", p.internalName)
                    put("name", p.name)
                    put("repoUrl", p.repoUrl)
                    put("downloadUrl", p.downloadUrl)
                    put("version", p.version)
                    put("filePath", p.filePath)
                    if (p.iconUrl != null) put("iconUrl", p.iconUrl)
                }
            )
        }
        prefs(context).edit { putString(KEY_PLUGINS, array.toString()) }
    }
}
