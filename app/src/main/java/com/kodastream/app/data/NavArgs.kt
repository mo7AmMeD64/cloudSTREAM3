package com.kodastream.app.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** What the details screen needs to know before it has loaded anything. */
data class DetailsArgs(
    val apiName: String,
    val url: String,
    val title: String?,
    val posterUrl: String?,
    val posterHeaders: Map<String, String>?,
)

/** What the player needs to start looking for links. */
data class PlayerArgs(
    val apiName: String,
    /** The `data` string a provider handed out in load(); passed straight to loadLinks(). */
    val data: String,
    val title: String,
    val subtitle: String?,
    /** Used to remember where playback stopped. */
    val resumeKey: String,
    /** What the watch history should record for this playback, or null to not record. */
    val history: HistoryStore.HistoryEntry? = null,
    /** A subtitle file downloaded beside the video, for offline playback. */
    val offlineSubtitleUri: String? = null,
    /** The series' episode list when this is an episode, for prev/next and the episodes panel. */
    val queue: EpisodeQueue? = null,
)

/**
 * In-memory hand-over of screen arguments.
 *
 * Provider urls and `data` strings can be long JSON blobs. Putting them in a
 * navigation route means escaping them and hoping nothing is double-decoded, so
 * the route only carries a small id and the real value is looked up here.
 */
object NavArgs {
    private val counter = AtomicInteger(0)
    private val details = ConcurrentHashMap<Int, DetailsArgs>()
    private val players = ConcurrentHashMap<Int, PlayerArgs>()

    fun put(args: DetailsArgs): Int = counter.incrementAndGet().also { details[it] = args }

    fun put(args: PlayerArgs): Int = counter.incrementAndGet().also { players[it] = args }

    fun details(id: Int): DetailsArgs? = details[id]

    fun player(id: Int): PlayerArgs? = players[id]
}
