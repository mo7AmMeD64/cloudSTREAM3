package com.kodastream.app.manga

import kotlin.math.abs

/** Extension library versions this app can run. */
internal val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

/**
 * Reads an extension lib version the same way Mihon does: the manifest float as its
 * shortest decimal ("1.6", not 1.600000023841858), otherwise the package version's
 * leading segments ("1.6.39" -> "1.6"). A two-segment value ("1.6") is already a lib
 * version and is kept whole. Pure so JVM tests can reach it.
 */
internal fun parseLibVersion(metaFloat: Float?, versionName: String?): Double? {
    if (metaFloat != null && metaFloat != 0f) return metaFloat.toString().toDoubleOrNull()
    val name = versionName?.takeIf { it.isNotBlank() } ?: return null
    val base = if (name.count { it == '.' } >= 2) name.substringBeforeLast('.') else name
    return base.toDoubleOrNull()
}

internal fun isSupportedLib(lib: Double?): Boolean =
    lib != null && SUPPORTED_LIB_VERSIONS.any { abs(lib - it) < 0.001 }

/** Compares lib versions with the same tolerance used by [isSupportedLib]. */
internal fun isNewerLib(candidate: Double?, current: Double?): Boolean =
    candidate != null && current != null && candidate - current > 0.001
