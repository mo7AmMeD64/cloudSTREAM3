package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.kodastream.app.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.settings.Globals

/**
 * The slice of the original AppContextUtils that the player calls. The rest
 * of that file is the result screen / provider settings / Android TV watch
 * next integration and has no caller here.
 */
object AppContextUtils {

    /** Sort subtitles by names */
    fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
        return subs.sortedWith(
            compareBy { subtitle: SubtitleData -> subtitle.originalName }
                .thenBy { subtitle: SubtitleData -> subtitle.nameSuffix })
    }

    fun Context.shouldShowPlayerMetadata(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(getString(R.string.show_player_metadata_key), true)
    }

    fun Context.getShortSeasonText(episode: Int?, season: Int?): String? {
        val rEpisode = if (episode == 0) null else episode
        val rSeason = if (season == 0) null else season
        val seasonNameShort = getString(R.string.season_short)
        val episodeNameShort = getString(R.string.episode_short)
        return if (rEpisode != null && rSeason != null) {
            "$seasonNameShort${rSeason}:$episodeNameShort${rEpisode}"
        } else if (rEpisode != null) {
            "$episodeNameShort$rEpisode"
        } else null
    }

    fun Context.isUsingMobileData(): Boolean {
        val connectionManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork: Network? = connectionManager.activeNetwork
            val networkCapabilities = connectionManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                    !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connectionManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) return
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.requestAudioFocus(focusRequest)
        } else {
            val audioManager: AudioManager =
                getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private var currentAudioFocusRequest: AudioFocusRequest? = null
    private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    var onAudioFocusEvent = Event<Boolean>()

    private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
        if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
        currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
            onAudioFocusEvent.invoke(
                when (it) {
                    AudioManager.AUDIOFOCUS_GAIN -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
                    else -> true
                }
            )
        }
        return currentAudioFocusChangeListener
    }

    fun getFocusRequest(): AudioFocusRequest? {
        if (currentAudioFocusRequest != null) return currentAudioFocusRequest
        currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(
                    AudioAttributes.Builder().run {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        build()
                    }
                )
                setAcceptsDelayedFocusGain(true)
                getAudioListener()?.let { setOnAudioFocusChangeListener(it) }
                build()
            }
        } else null
        return currentAudioFocusRequest
    }

    /** Sets the focus to the negative button when in TV and Emulator layout. */
    fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
        if (!Globals.isLayout(Globals.TV or Globals.EMULATOR)) return
        this.getButton(buttonFocus).run {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    /** Inlined from AppContextUtils in the original. */
    fun String?.html(): Spanned {
        if (this == null) return "".toSpanned()
        return try {
            HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } catch (e: Exception) {
            logError(e)
            this.toSpanned()
        }
    }
}
