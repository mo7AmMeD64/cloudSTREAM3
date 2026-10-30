package com.kodastream.app.ui.details

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType

const val WVC_PACKAGE_NAME = "com.instantbits.cast.webvideo"
const val WVC_MARKET_URI = "market://details?id=com.instantbits.cast.webvideo"
const val WVC_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo"

/**
 * Pure helper to resolve the MIME type expected by Web Video Caster.
 * Uses "application/x-mpegURL" for M3U8 streams and "video/mp4" for direct videos.
 */
fun wvcMimeType(linkType: ExtractorLinkType): String = when (linkType) {
    ExtractorLinkType.M3U8 -> "application/x-mpegURL"
    else -> "video/mp4"
}

/**
 * Builds a clean map of HTTP headers with case-insensitive Referer de-duplication.
 */
fun buildWvcHeadersMap(headers: Map<String, String>, referer: String?): Map<String, String> {
    val map = linkedMapOf<String, String>()
    headers.forEach { (key, value) ->
        if (key.isNotBlank()) {
            map[key] = value.replace('\n', ' ').replace('\r', ' ')
        }
    }
    if (!referer.isNullOrBlank() && headers.keys.none { it.equals("referer", ignoreCase = true) }) {
        map["Referer"] = referer.trim()
    }
    return map
}

/** Pure data holder representing parameters prepared for Web Video Caster. */
data class WvcCastParams(
    val url: String,
    val mimeType: String,
    val title: String,
    val headers: Map<String, String>,
    val packageName: String = WVC_PACKAGE_NAME,
)

/** Extracts and normalizes parameters from an [ExtractorLink] for testing and dispatch. */
fun prepareWvcCastParams(link: ExtractorLink, title: String): WvcCastParams = WvcCastParams(
    url = link.url,
    mimeType = wvcMimeType(link.type),
    title = title,
    headers = buildWvcHeadersMap(link.headers, link.referer),
    packageName = WVC_PACKAGE_NAME,
)

/** Checks if Web Video Caster is installed on the device. */
fun isWvcInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getLaunchIntentForPackage(WVC_PACKAGE_NAME) != null
}.getOrDefault(false)

/** Opens the Web Video Caster page on Google Play Store (market scheme first, then web URL). */
fun openWvcPlayStore(context: Context) {
    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse(WVC_MARKET_URI)).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val startedMarket = runCatching {
        context.startActivity(marketIntent)
        true
    }.getOrDefault(false)

    if (!startedMarket) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(WVC_PLAY_STORE_URL)).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}

/**
 * Constructs an ACTION_VIEW intent targeting Web Video Caster with MIME type,
 * title, and header extras.
 */
fun buildWvcIntent(
    link: ExtractorLink,
    title: String,
    context: Context? = null,
): Intent {
    val mimeType = wvcMimeType(link.type)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage(WVC_PACKAGE_NAME)
        setDataAndType(Uri.parse(link.url), mimeType)
        putExtra("title", title)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra("secure_uri", true)
        if (context != null && context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    val headerMap = buildWvcHeadersMap(link.headers, link.referer)
    if (headerMap.isNotEmpty()) {
        val bundle = Bundle()
        headerMap.forEach { (name, value) ->
            bundle.putString(name, value)
        }
        intent.putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
        intent.putExtra("headers", bundle)
    }

    return intent
}

/**
 * Sends a stream link to Web Video Caster.
 * If Web Video Caster is not installed, opens its Google Play Store page and returns false.
 * If installed, launches the app with the stream and returns true.
 */
fun castToWebVideoCaster(
    context: Context,
    link: ExtractorLink,
    title: String,
): Boolean {
    if (!isWvcInstalled(context)) {
        openWvcPlayStore(context)
        return false
    }

    val intent = buildWvcIntent(link, title, context)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        openWvcPlayStore(context)
        false
    }
}
