package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeyClass
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * The trimmed DataStoreHelper the ported player uses: playback speed,
 * resize mode, watched-episode positions (PosDur) and the watch-state key
 * store - all through CloudStream's own DataStore, same folder/key layout
 * as the original. Accounts, favorites, resume-watching lists and the other
 * app-module subsystems stayed behind.
 */
const val VIDEO_POS_DUR = "video_pos_dur"
const val VIDEO_WATCH_STATE = "video_watch_state"
private const val NEXT_WATCH_EPISODE_PERCENTAGE = 90L

class UserPreferenceDelegate<T : Any>(
    private val key: String,
    private val default: T,
) {
    private val klass: KClass<out T> = default::class
    private val realKey get() = "${DataStoreHelper.currentAccount}/$key"
    operator fun getValue(self: Any?, property: KProperty<*>) =
        getKeyClass(realKey, klass.java) ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        value: T,
    ) = setKey(realKey, value)
}

object DataStoreHelper {
    var playBackSpeed: Float by UserPreferenceDelegate("playback_speed", 1.0f)
    var resizeMode: Int by UserPreferenceDelegate("resize_mode", 0)

    /** KodaStream has a single account; the original's key prefix stays. */
    val currentAccount: String get() = "0"

    @Serializable
    data class PosDur(
        @JsonProperty("position") @SerialName("position") val position: Long,
        @JsonProperty("duration") @SerialName("duration") val duration: Long,
    )

    fun PosDur.fixVisual(): PosDur {
        if (duration <= 0) return PosDur(0, duration)
        val percentage = position * 100 / duration
        return when {
            percentage <= 1 -> PosDur(0, duration)
            percentage <= 5 -> PosDur(5 * duration / 100, duration)
            percentage >= 95 -> PosDur(duration, duration)
            else -> this
        }
    }

    fun setViewPos(id: Int?, pos: Long, dur: Long) {
        if (id == null) return
        if (dur < 30_000) return // too short
        setKey("$currentAccount/$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
    }

    /**
     * Positions plus the episode's watch state. The original also maintained
     * a resume-watching list through the result subsystem; KodaStream keeps
     * resume state in its own HistoryStore, so that part stays behind.
     */
    fun setViewPosAndResume(id: Int?, position: Long, duration: Long, currentEpisode: Any?, nextEpisode: Any?) {
        setViewPos(id, position, duration)
        val percentage = position * 100L / duration
        // Watching past 90% counts as watched, same rule as the original.
        if (percentage >= NEXT_WATCH_EPISODE_PERCENTAGE) {
            setVideoWatchState(id, VideoWatchState.Watched)
        }
    }

    fun getViewPos(id: Int?): PosDur? {
        if (id == null) return null
        return getKey<PosDur>("$currentAccount/$VIDEO_POS_DUR", id.toString(), null)
    }

    fun getVideoWatchState(id: Int?): VideoWatchState? {
        if (id == null) return null
        return getKey<VideoWatchState>("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), null)
    }

    fun setVideoWatchState(id: Int?, watchState: VideoWatchState) {
        if (id == null) return
        // None == No key
        if (watchState == VideoWatchState.None) {
            removeKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString())
        } else {
            setKey("$currentAccount/$VIDEO_WATCH_STATE", id.toString(), watchState)
        }
    }
}
