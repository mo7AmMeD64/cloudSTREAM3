package com.kodastream.app.manga

/**
 * Store signing keys are lowercase hex SHA-256 values, but a store may write them with
 * uppercase, colons or spaces. Compare the normalised forms on both sides.
 */
internal fun normalizeSignature(value: String): String =
    value.trim().lowercase().removePrefix("sha256:").filterNot { it == ':' || it.isWhitespace() }

internal fun signatureMatches(signatures: Collection<String>, key: String): Boolean {
    val normalized = normalizeSignature(key)
    return normalized.isNotEmpty() && signatures.any { normalizeSignature(it) == normalized }
}
