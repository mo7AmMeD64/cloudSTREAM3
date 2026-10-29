package com.kodastream.app.ui.components

/**
 * Wraps a title in Unicode first-strong isolates (FSI ... PDI), so mixed
 * Arabic/English text keeps its punctuation and numbers in the order the
 * provider wrote them instead of being reordered by the surrounding
 * paragraph direction.
 */
fun bidiSafe(text: String): String = "\u2068$text\u2069"
