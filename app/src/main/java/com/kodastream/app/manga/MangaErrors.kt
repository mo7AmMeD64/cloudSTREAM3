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
