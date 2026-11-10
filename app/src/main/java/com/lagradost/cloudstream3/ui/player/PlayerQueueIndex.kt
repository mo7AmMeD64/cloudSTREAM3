package com.lagradost.cloudstream3.ui.player

/**
 * Index arithmetic for the player's episode queue, kept free of Android types so it
 * can be unit-tested. The index never leaves `0..lastIndex`, even when the queue
 * shrinks while an index from the previous list is still held.
 */

/** Clamps [index] into the queue; an empty queue maps to 0. */
internal fun clampQueueIndex(index: Int, queueSize: Int): Int =
    index.coerceIn(0, (queueSize - 1).coerceAtLeast(0))

/** The index of the previous episode, or null when [index] is already the first one. */
internal fun previousQueueIndex(index: Int): Int? = if (index > 0) index - 1 else null

/** The index of the next episode, or null when [index] is already the last one. */
internal fun nextQueueIndex(index: Int, queueSize: Int): Int? =
    if (index < queueSize - 1) index + 1 else null
