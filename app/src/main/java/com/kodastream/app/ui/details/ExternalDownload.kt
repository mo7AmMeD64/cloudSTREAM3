package com.kodastream.app.ui.details

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

/** A download manager known to accept a link through ACTION_VIEW. */
data class DownloadManager(val packageName: String, val label: String)

val KNOWN_DOWNLOAD_MANAGERS = listOf(
    DownloadManager("idm.internet.download.manager.plus", "1DM+"),
    DownloadManager("idm.internet.download.manager", "1DM"),
    DownloadManager("idm.internet.download.manager.adm.lite", "1DM Lite"),
    DownloadManager("com.dv.adm", "ADM"),
    DownloadManager("com.dv.adm.pay", "ADM Pro"),
)

/** The remembered manager first, every other manager in its original order. */
fun orderDownloadManagers(
    installed: List<DownloadManager>,
    remembered: String?,
): List<DownloadManager> = installed.sortedBy { it.packageName != remembered }

fun isHttpUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/**
 * Title plus episode label plus an extension guessed from the URL. Illegal
 * filename characters become spaces; blank input falls back to "video".
 */
fun downloadFileName(
    title: String?,
    episodeLabel: String?,
    url: String,
    isM3u8: Boolean,
): String {
    val base = listOfNotNull(
        title?.trim()?.takeIf { it.isNotEmpty() },
        episodeLabel?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" - ")

    val clean = base
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "video" }

    val path = url.substringBefore('?').substringBefore('#')
    val fromUrl = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    val extension = when {
        isM3u8 -> "m3u8"
        fromUrl.length in 2..4 && fromUrl.all(Char::isLetterOrDigit) -> fromUrl
        else -> "mp4"
    }
    return "$clean.$extension"
}

/** `Name: value` lines; the referer is added once when it is not already a header. */
fun downloadHeaderLines(headers: Map<String, String>, referer: String?): List<String> {
    val lines = headers
        .filterKeys { it.isNotBlank() }
        .map { (name, value) -> "$name: ${value.replace('\n', ' ').replace('\r', ' ')}" }
        .toMutableList()
    if (!referer.isNullOrBlank() && headers.keys.none { it.equals("referer", ignoreCase = true) }) {
        lines += "Referer: ${referer.trim()}"
    }
    return lines
}

/** Case-insensitive header lookup, for the extras that want a single value. */
fun headerValue(headers: Map<String, String>, name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

fun installedDownloadManagers(context: Context): List<DownloadManager> =
    KNOWN_DOWNLOAD_MANAGERS.filter { manager ->
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                    .setPackage(manager.packageName),
                0,
            )
        }.getOrNull() != null
    }

/**
 * Best-effort ACTION_VIEW with the extras download managers look for. No
 * extra is a standard, so each group is allowed to fail silently.
 */
fun externalDownloadIntent(
    context: Context,
    link: ExtractorLink,
    fileName: String,
): Intent {
    val uri = Uri.parse(link.url)
    val intent = Intent(Intent.ACTION_VIEW)
    if (link.type == ExtractorLinkType.M3U8) {
        intent.setData(uri)
    } else {
        intent.setDataAndType(uri, "video/*")
    }
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val lines = downloadHeaderLines(link.headers, link.referer)
    val referer = link.referer.takeIf { it.isNotBlank() }
        ?: headerValue(link.headers, "Referer")
    val userAgent = headerValue(link.headers, "User-Agent")
    runCatching {
        intent.putExtra("com.android.extra.filename", fileName)
        if (lines.isNotEmpty()) {
            intent.putExtra("headers", lines.toTypedArray())
            val bundle = Bundle()
            lines.forEach { line ->
                val name = line.substringBefore(':').trim()
                if (name.isNotEmpty()) bundle.putString(name, line.substringAfter(':').trim())
            }
            intent.putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
        }
        if (referer != null) {
            intent.putExtra("referer", referer)
            intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(referer))
        }
        if (userAgent != null) intent.putExtra("user_agent", userAgent)
    }
    return intent
}

/** Opens one known manager. False when nothing handles the link. */
fun startDownloadWith(
    context: Context,
    link: ExtractorLink,
    fileName: String,
    packageName: String,
): Boolean {
    val intent = externalDownloadIntent(context, link, fileName).setPackage(packageName)
    if (context.packageManager.resolveActivity(intent, 0) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Opens the system chooser. False when nothing handles the link. */
fun startDownloadChooser(
    context: Context,
    link: ExtractorLink,
    fileName: String,
    title: String,
): Boolean {
    val intent = externalDownloadIntent(context, link, fileName)
    if (context.packageManager.resolveActivity(intent, 0) == null) return false
    val chooser = Intent.createChooser(intent, title)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(chooser) }.isSuccess
}

/** Plain-text clipboard copy; marked sensitive on Android 13 where the extra exists. */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    true
}.getOrDefault(false)
