package com.kodastream.app.manga.extension

import com.kodastream.app.manga.rootMessage
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Touches the lazily built HTTP members an extension's bridge class needs. Only linkage
 * errors are incompatibilities; a network or parse error during the probe is ignored.
 */
internal object MangaLinkProbe {
    fun probe(source: Source): MangaExtension.Loaded.LinkFailure? {
        if (source !is HttpSource) return null
        return check {
            source.client
            source.headers
        }
    }

    fun check(touch: () -> Unit): MangaExtension.Loaded.LinkFailure? = try {
        touch()
        null
    } catch (e: Throwable) {
        if (e is LinkageError || e is ClassNotFoundException) {
            MangaExtension.Loaded.LinkFailure(
                message = rootMessage(e),
                stackTrace = e.stackTraceToString(),
            )
        } else {
            null
        }
    }
}
