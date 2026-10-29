package com.kodastream.app.data

import android.content.Context
import android.util.Log
import com.kodastream.app.BuildConfig
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** A newer release found on GitHub. */
data class AppUpdate(
    /** Version without the leading "v", for example "0.3.0". */
    val version: String,
    val notes: String?,
    /** The release's APK asset. */
    val url: String,
)

/** The decision-relevant fields of one GitHub release, with no Android types. */
internal data class Release(
    val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val apkUrl: String?,
    val notes: String?,
)

/** The release to offer, or the one-line reason there is none. */
internal data class UpdateDecision(val update: AppUpdate?, val reason: String? = null)

/**
 * Checks the project's official GitHub releases. A release found earlier is
 * offered on every start without a network call; GitHub is asked again at most
 * every six hours. Every failure is silent: an app start must never wait on,
 * or complain about, the network.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val OWNER_REPO = "mo7AmMeD64/cloudSTREAM3"
    private const val LATEST_URL = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /** CI publishes `build-<run>` tags; only `v1.2.3` is an official release. */
    private val OFFICIAL_TAG = Regex("^v\\d+(?:\\.\\d+)*$")

    /**
     * The update to offer on this start, stored or freshly checked. Null means
     * no dialog: nothing newer, a skipped version, or no usable response yet.
     */
    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val skipped = AppPrefs.skippedUpdateVersion(context)
        val stored = AppPrefs.storedUpdate(context)
        val now = System.currentTimeMillis()

        if (now - AppPrefs.lastUpdateCheck(context) < CHECK_INTERVAL_MS) {
            return@withContext storedDecision(stored, current, skipped, "throttled")
        }

        val request = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .apply { AppPrefs.updateEtag(context)?.let { header("If-None-Match", it) } }
            .build()

        val response = try {
            app.baseClient.newCall(request).execute()
        } catch (t: Throwable) {
            return@withContext storedDecision(stored, current, skipped, "network error: ${t.message}")
        }

        response.use {
            val code = response.code
            if (!isSuccessfulCheck(code)) {
                // Not a completed check: retry on the next start, keep any stored offer.
                return@withContext storedDecision(stored, current, skipped, "HTTP $code")
            }
            AppPrefs.setLastUpdateCheck(context, now)

            if (code == 304) {
                return@withContext storedDecision(stored, current, skipped, "not modified")
            }
            if (code == 404) {
                // No official release exists yet.
                AppPrefs.setStoredUpdate(context, null)
                AppPrefs.setUpdateEtag(context, null)
                Log.d(TAG, "no dialog: no official release")
                return@withContext null
            }

            val json = response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json == null) {
                return@withContext storedDecision(stored, current, skipped, "empty or invalid JSON")
            }

            val decision = decide(release(json), current, skipped)
            AppPrefs.setUpdateEtag(context, response.header("ETag"))
            AppPrefs.setStoredUpdate(context, decision.update)
            if (decision.update != null) {
                Log.d(TAG, "offering ${decision.update.version}")
            } else {
                Log.d(TAG, "no dialog: ${decision.reason}")
            }
            decision.update
        }
    }

    /**
     * A successful response. A 403, a 5xx or a network error must not move the
     * last-checked time, so a failure is retried on the next start.
     */
    internal fun isSuccessfulCheck(code: Int?): Boolean =
        code == 200 || code == 304 || code == 404

    /** Only `v` plus dotted digits is an official release; `build-15` never is. */
    internal fun officialVersion(tag: String): String? =
        tag.trim().takeIf { OFFICIAL_TAG.matches(it) }?.removePrefix("v")

    internal fun decide(release: Release, currentVersion: String, skippedVersion: String?): UpdateDecision {
        if (release.draft || release.prerelease) return UpdateDecision(null, "pre-release or draft")
        val version = officialVersion(release.tag) ?: return UpdateDecision(null, "tag is not a version")
        val apk = release.apkUrl?.takeIf { it.startsWith("https://") }
            ?: return UpdateDecision(null, "no APK asset")
        if (!isNewerVersion(version, currentVersion)) return UpdateDecision(null, "not newer")
        if (version == skippedVersion) return UpdateDecision(null, "skipped")
        return UpdateDecision(AppUpdate(version, release.notes, apk))
    }

    /** The stored release is offered again only while it is still newer and not skipped. */
    internal fun decideStored(stored: AppUpdate?, currentVersion: String, skippedVersion: String?): UpdateDecision {
        if (stored == null) return UpdateDecision(null, "no stored update")
        if (!isNewerVersion(stored.version, currentVersion)) {
            return UpdateDecision(null, "stored update not newer")
        }
        if (stored.version == skippedVersion) return UpdateDecision(null, "skipped")
        return UpdateDecision(stored)
    }

    private fun storedDecision(
        stored: AppUpdate?,
        currentVersion: String,
        skippedVersion: String?,
        reason: String,
    ): AppUpdate? {
        val decision = decideStored(stored, currentVersion, skippedVersion)
        Log.d(
            TAG,
            decision.update?.let { "$reason: using stored ${it.version}" }
                ?: "no dialog: $reason, ${decision.reason}",
        )
        return decision.update
    }

    private fun release(json: JSONObject) = Release(
        tag = json.optString("tag_name"),
        draft = json.optBoolean("draft"),
        prerelease = json.optBoolean("prerelease"),
        apkUrl = apkUrl(json),
        notes = cleanNotes(json.optString("body")),
    )

    private fun apkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.startsWith("https://")) return url
        }
        return null
    }

    /** Keeps the release notes short and readable as plain text. */
    private fun cleanNotes(body: String): String? {
        val link = Regex("\\[([^]]+)]\\([^)]*\\)")
        val lines = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") || it.contains("Full Changelog", ignoreCase = true) }
            .map { line ->
                line.replace(link) { it.groupValues[1] }
                    .removePrefix("* ")
                    .removePrefix("- ")
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .take(6)
            .toList()
        return lines.joinToString("\n").take(400).trim().ifBlank { null }
    }

    /** Numeric comparison, so "0.10.0" is newer than "0.9.9". */
    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val new = versionParts(candidate)
        val old = versionParts(current)
        for (i in 0 until maxOf(new.size, old.size)) {
            val a = new.getOrElse(i) { 0 }
            val b = old.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+', '_')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
