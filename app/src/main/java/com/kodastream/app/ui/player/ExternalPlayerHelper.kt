package com.kodastream.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

// Resolves the MIME type for external player dispatch.
// Returns "application/x-mpegURL" for M3U8 streams and "video" wildcard for other video formats.
fun resolveExternalPlayerMimeType(link: ExtractorLink): String {
    return resolveExternalPlayerMimeType(
        isM3u8 = link.isM3u8 || link.type == ExtractorLinkType.M3U8,
        url = link.url,
    )
}

// Pure helper to resolve external player MIME type from flags and URL.
fun resolveExternalPlayerMimeType(isM3u8: Boolean, url: String?): String {
    val isM3u8Url = url?.contains(".m3u8", ignoreCase = true) == true
    return if (isM3u8 || isM3u8Url) "application/x-mpegURL" else "video/*"
}

// Pure data holder for external player parameters.
data class ExternalPlayerParams(
    val url: String,
    val mimeType: String,
    val title: String,
    val position: Long,
)

// Extracts and normalizes parameters from an ExtractorLink for external playback.
fun prepareExternalPlayerParams(
    link: ExtractorLink,
    title: String? = null,
    startPositionMs: Long = 0L,
): ExternalPlayerParams = ExternalPlayerParams(
    url = link.url,
    mimeType = resolveExternalPlayerMimeType(link),
    title = title?.ifBlank { null } ?: link.name,
    position = maxOf(0L, startPositionMs),
)

// Checks whether a URL is a valid HTTP/HTTPS streaming link.
fun isHttpUrl(url: String?): Boolean =
    url?.startsWith("http://", ignoreCase = true) == true ||
    url?.startsWith("https://", ignoreCase = true) == true

// Builds the ACTION_VIEW intent for opening a video link in an external player (e.g. VLC, MX Player).
fun buildExternalPlayerIntent(
    link: ExtractorLink,
    title: String,
    startPositionMs: Long,
    context: Context? = null,
): Intent {
    val mimeType = resolveExternalPlayerMimeType(link)
    val uri = Uri.parse(link.url)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        putExtra("position", startPositionMs)
        putExtra("title", title)
        if (context != null && context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

// Launches an external player chooser (e.g., VLC, MX Player) with the stream URL, title, and position.
fun openInExternalPlayer(
    context: Context,
    link: ExtractorLink,
    title: String,
    startPositionMs: Long,
) {
    val intent = buildExternalPlayerIntent(link, title, startPositionMs, context)
    val chooser = Intent.createChooser(intent, "Open with...").apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching {
        context.startActivity(chooser)
    }
}
