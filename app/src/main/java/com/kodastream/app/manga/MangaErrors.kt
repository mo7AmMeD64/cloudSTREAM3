package com.kodastream.app.manga

/** The deepest cause message, where class-loading failures actually explain themselves. */
internal fun rootMessage(error: Throwable): String {
    var current = error
    val seen = mutableSetOf<Throwable>()
    while (seen.add(current)) {
        current = current.cause ?: break
    }
    return current.message?.takeIf { it.isNotBlank() } ?: current.javaClass.simpleName
}

/**
 * True when the failure looks like a Cloudflare check the user has to pass in a
 * browser: the interceptor gave up on it, or the site answered 403 or 503 (what
 * it sends once the interceptor has stopped trying for a minute).
 */
internal fun isCloudflareBlock(error: Throwable): Boolean {
    var current: Throwable? = error
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException) return true
        if (current is eu.kanade.tachiyomi.network.HttpException &&
            (current.code == 403 || current.code == 503)
        ) return true
        current = current.cause
    }
    return false
}
