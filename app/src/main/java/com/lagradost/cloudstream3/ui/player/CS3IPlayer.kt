@file:Suppress("DEPRECATION")

package com.lagradost.cloudstream3.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.widget.FrameLayout
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.C.TRACK_TYPE_AUDIO
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.C.TRACK_TYPE_VIDEO
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
// import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer.STATE_ENABLED
import androidx.media3.exoplayer.Renderer.STATE_STARTED
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.ui.SubtitleView
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.kodastream.app.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.fixSubtitleAlignment
import com.lagradost.cloudstream3.ui.player.live.LiveHelper
import com.lagradost.cloudstream3.ui.player.live.PREFERRED_LIVE_OFFSET
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyle
import com.lagradost.cloudstream3.utils.AppContextUtils.isUsingMobileData
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.CLEARKEY_DRM_UUID
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.PLAYREADY_DRM_UUID
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.cloudstream3.utils.WIDEVINE_DRM_UUID
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.uuid.toJavaUuid

const val TAG = "CS3ExoPlayer"
const val PREFERRED_AUDIO_LANGUAGE_KEY = "preferred_audio_language"

/** toleranceBeforeUs – The maximum time that the actual position seeked to may precede the
 * requested seek position, in microseconds. Must be non-negative. */
const val toleranceBeforeUs = 300_000L

/**
 * toleranceAfterUs – The maximum time that the actual position seeked to may exceed the requested
 * seek position, in microseconds. Must be non-negative.
 */
const val toleranceAfterUs = 300_000L

@OptIn(UnstableApi::class)
class CS3IPlayer : IPlayer {
    private var playerListener: Player.Listener? = null
    private var isPlaying = false
    private var exoPlayer: ExoPlayer? = null
        set(value) {
            // If the old value is not null then the player has not been properly released.
            debugAssert(
                { field != null && value != null },
                { "Previous player instance should be released!" })
            field = value
        }

    var cacheSize = 0L
    var simpleCacheSize = 0L
    var videoBufferMs = 0L

    val imageGenerator = IPreviewGenerator.new()

    private val seekActionTime = 30000L
    private val isMediaSeekable
        get() = exoPlayer?.let {
            it.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) && it.isCurrentMediaItemSeekable
        } ?: false

    private var ignoreSSL: Boolean = true
    private var playBackSpeed: Float = 1.0f

    /**
     * Shared compressor instance — only created when the setting is enabled.
     * When disabled, this stays null and the audio pipeline is completely
     * unmodified (no custom AudioProcessor, no custom AudioSink at all).
     */
    var compressor: DynamicRangeCompressor? = null
        private set

    private var lastMuteVolume: Float = 1.0f

    private var currentLink: ExtractorLink? = null
    private var currentDownloadedFile: ExtractorUri? = null
    private var hasUsedFirstRender = false

    private var currentWindow: Int = 0
    private var playbackPosition: Long = 0

    private val subtitleHelper = PlayerSubtitleHelper()

    /** If we want to play the audio only in the background when the app is not open */
    private var isAudioOnlyBackground = false

    /**
     * This is a way to combine the MediaItem and its duration for the concatenating MediaSource.
     * @param durationUs does not matter if only one slice is present, since it will not concatenate
     * */
    data class MediaItemSlice(
        val mediaItem: MediaItem,
        val durationUs: Long,
        val drm: DrmMetadata? = null
    )

    data class DrmMetadata(
        val kid: String? = null,
        val key: String? = null,
        val uuid: UUID,
        val kty: String? = null,
        val licenseUrl: String? = null,
        val keyRequestParameters: HashMap<String, String>,
    )

    override fun getDuration(): Long? = exoPlayer?.duration
    override fun getPosition(): Long? = exoPlayer?.currentPosition
    override fun getIsPlaying(): Boolean = isPlaying
    override fun getPlaybackSpeed(): Float = playBackSpeed

    /**
     * Tracks reported to be used by exoplayer, since sometimes it has a mind of it's own when selecting subs.
     * String = id (without exoplayer track number)
     * Boolean = if it's active
     * */
    private var playerSelectedSubtitleTracks = listOf<Pair<String, Boolean>>()
    private var requestedListeningPercentages: List<Int>? = null

    private var eventHandler: ((PlayerEvent) -> Unit)? = null

    @AnyThread
    fun event(event: PlayerEvent) {
        // Ensure that all work is done on the main thread.
        if (Looper.getMainLooper().isCurrentThread) {
            eventHandler?.invoke(event)
        } else runOnMainThread {
            eventHandler?.invoke(event)
        }
    }

    /**
     * As initCallbacks and releaseCallbacks must always be done,
     * we use this to say that the player is in use.
     * */
    @Volatile
    var isPlayerActive: Boolean = false

    override fun releaseCallbacks() {
        eventHandler = null
        if (isPlayerActive) {
            isPlayerActive = false
        }
    }

    @AnyThread
    override fun initCallbacks(
        @MainThread eventHandler: ((PlayerEvent) -> Unit),
        requestedListeningPercentages: List<Int>?,
    ) {
        this.requestedListeningPercentages = requestedListeningPercentages
        this.eventHandler = eventHandler
        if (!isPlayerActive) {
            isPlayerActive = true
        }
    }

    fun String.stripTrackId(): String {
        return this.replace(Regex("""^\d+:"""), "")
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleHelper.initSubtitles(subView, subHolder, style)
    }

    override fun getPreview(fraction: Float): Bitmap? {
        return imageGenerator.getPreviewImage(fraction)
    }

    override fun hasPreview(): Boolean {
        // No previews on livestreams because the previews get outdated
        if (exoPlayer?.isCurrentMediaItemDynamic == true) {
            return false
        }
        return imageGenerator.hasPreview()
    }

    override fun loadPlayer(
        context: Context,
        sameEpisode: Boolean,
        link: ExtractorLink?,
        data: ExtractorUri?,
        startPosition: Long?,
        subtitles: Set<SubtitleData>,
        subtitle: SubtitleData?,
        autoPlay: Boolean?,
        preview: Boolean,
    ) {
        Log.i(TAG, "loadPlayer")
        if (sameEpisode) {
            saveData()
        } else {
            currentSubtitles = subtitle
            playbackPosition = 0
        }

        startPosition?.let {
            playbackPosition = it
        }

        // we want autoplay because of TV and UX
        isPlaying = autoPlay ?: isPlaying

        // release the current exoplayer and cache
        releasePlayer()

        if (link != null) {
            // only video support atm
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(link, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }

            loadOnlinePlayer(context, link)
        } else if (data != null) {
            (imageGenerator as? PreviewGenerator)?.let { gen ->
                if (preview) {
                    gen.load(context, data, sameEpisode)
                } else {
                    gen.clear(sameEpisode)
                }
            }
            loadOfflinePlayer(context, data)
        } else {
            throw IllegalArgumentException("Requires link or uri")
        }

    }

    override fun setActiveSubtitles(subtitles: Set<SubtitleData>) {
        Log.i(TAG, "setActiveSubtitles ${subtitles.size}")
        subtitleHelper.setAllSubtitles(subtitles)
    }

    private var currentSubtitles: SubtitleData? = null

    private fun List<Tracks.Group>.getTrack(id: String?): Pair<TrackGroup, Int>? {
        if (id == null) return null
        // This beast of an expression does:
        // 1. Filter all audio tracks
        // 2. Get all formats in said audio tacks
        // 3. Gets all ids of the formats
        // 4. Filters to find the first audio track with the same id as the audio track we are looking for
        // 5. Returns the media group and the index of the audio track in the group
        return this.firstNotNullOfOrNull { group ->
            (0 until group.mediaTrackGroup.length).map {
                group.getTrackFormat(it) to it
            }.firstOrNull {
                // The format id system is "trackNumber:trackID"
                // The track number is not generated by us so we filter it out
                it.first.id?.stripTrackId() == id
            }
                ?.let { group.mediaTrackGroup to it.second }
        }
    }

    override fun setMaxVideoSize(width: Int, height: Int, id: String?) {
        if (id != null) {
            val videoTrack =
                exoPlayer?.currentTracks?.groups?.filter { it.type == TRACK_TYPE_VIDEO }
                    ?.getTrack(id)

            if (videoTrack != null) {
                exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setOverrideForType(
                        TrackSelectionOverride(
                            videoTrack.first,
                            videoTrack.second
                        )
                    )
                    ?.build()
                    ?: return
                return
            }
        }

        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setMaxVideoSize(width, height)
            ?.build()
            ?: return
    }

    override fun setPreferredAudioTrack(trackLanguage: String?, id: String?, formatIndex: Int?) {
        preferredAudioTrackLanguage = trackLanguage
        id?.let { trackId ->
            val trackFormatIndex = formatIndex ?: 0
            exoPlayer?.currentTracks?.groups
                ?.filter { it.type == TRACK_TYPE_AUDIO }
                ?.find { group ->
                    group.getFormats().any { (format, _) ->
                        format.id == trackId
                    }
                }
                ?.let { group ->
                    exoPlayer?.trackSelectionParameters
                        ?.buildUpon()
                        ?.setOverrideForType(
                            TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackFormatIndex
                            )
                        )
                        ?.build()
                }
                ?.let { newParams ->
                    exoPlayer?.trackSelectionParameters = newParams
                    return
                }
        }
        // Fallback to language-based selection
        exoPlayer?.trackSelectionParameters = exoPlayer?.trackSelectionParameters
            ?.buildUpon()
            ?.setPreferredAudioLanguage(trackLanguage)
            ?.build() ?: return
    }

    /**
     * Gets all supported formats in a list
     * */
    private fun List<Tracks.Group>.getFormats(): List<Pair<Format, Int>> {
        return this.flatMap {
            it.getFormats()
        }
    }

    private fun Tracks.Group.getFormats(): List<Pair<Format, Int>> {
        return (0 until this.mediaTrackGroup.length).mapNotNull { i ->
            if (this.isSupported)
                this.mediaTrackGroup.getFormat(i) to i
            else null
        }
    }

    private fun Format.toAudioTrack(formatIndex: Int?): AudioTrack {
        return AudioTrack(
            this.id,
            this.label,
            this.language,
            this.sampleMimeType,
            this.channelCount,
            formatIndex ?: 0,
        )
    }

    private fun Format.toSubtitleTrack(): TextTrack {
        return TextTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.sampleMimeType,
        )
    }

    private fun Format.toVideoTrack(): VideoTrack {
        return VideoTrack(
            this.id?.stripTrackId(),
            this.label,
            this.language,
            this.width,
            this.height,
            this.sampleMimeType
        )
    }

    override fun getVideoTracks(): CurrentTracks {
        val allTrackGroups = exoPlayer?.currentTracks?.groups ?: emptyList()
        val videoTracks = allTrackGroups.filter { it.type == TRACK_TYPE_VIDEO }
            .getFormats()
            .map { it.first.toVideoTrack() }
        var currentAudioTrack: AudioTrack? = null
        val audioTracks = allTrackGroups.filter { it.type == TRACK_TYPE_AUDIO }
            .flatMap { group ->
                group.getFormats().map { (format, formatIndex) ->
                    val audioTrack = format.toAudioTrack(formatIndex)
                    if (group.isTrackSelected(formatIndex)) {
                        currentAudioTrack = audioTrack
                    }
                    audioTrack
                }
            }
        val textTracks = allTrackGroups.filter { it.type == TRACK_TYPE_TEXT }
            .getFormats()
            .map { it.first.toSubtitleTrack() }
        val currentTextTracks = textTracks.filter { track ->
            playerSelectedSubtitleTracks.any { it.second && it.first == track.id }
        }
        return CurrentTracks(
            exoPlayer?.videoFormat?.toVideoTrack(),
            currentAudioTrack,
            currentTextTracks,
            videoTracks,
            audioTracks,
            textTracks
        )
    }

    /**
     * @return True if the player should be reloaded
     * */
    override fun setPreferredSubtitles(subtitle: SubtitleData?): Boolean {
        Log.i(TAG, "setPreferredSubtitles init $subtitle")
        currentSubtitles = subtitle
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector ?: return false
        // Disable subtitles if null
        if (subtitle == null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(TRACK_TYPE_TEXT)
            )
            return false
        }
        // Handle subtitle based on status
        when (subtitleHelper.subtitleStatus(subtitle)) {
            SubtitleStatus.REQUIRES_RELOAD -> {
                Log.i(TAG, "setPreferredSubtitles REQUIRES_RELOAD")
                return true
            }

            SubtitleStatus.NOT_FOUND -> {
                Log.i(TAG, "setPreferredSubtitles NOT_FOUND")
                return true
            }

            SubtitleStatus.IS_ACTIVE -> {
                Log.i(TAG, "setPreferredSubtitles IS_ACTIVE")
                exoPlayer?.currentTracks?.groups
                    ?.filter { it.type == TRACK_TYPE_TEXT }
                    ?.getTrack(subtitle.getId())
                    ?.let { (trackGroup, trackIndex) ->
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(trackGroup, trackIndex))
                        )
                    }
                return false
            }
        }
    }

    private var currentSubtitleOffset: Long = 0

    override fun setSubtitleOffset(offset: Long) {
        currentSubtitleOffset = offset
        CustomDecoder.subtitleOffset = offset
        if (currentTextRenderer?.state == STATE_ENABLED || currentTextRenderer?.state == STATE_STARTED) {
            exoPlayer?.currentPosition?.also { pos ->
                // This seems to properly refresh all subtitles
                // It needs to be done as all subtitle cues with timings are pre-processed
                currentTextRenderer?.resetPosition(pos, false)
            }
        }
    }

    override fun getSubtitleOffset(): Long {
        return currentSubtitleOffset
    }

    override fun getSubtitleCues(): List<SubtitleCue> {
        return currentSubtitleDecoder?.getSubtitleCues() ?: emptyList()
    }

    override fun getCurrentPreferredSubtitle(): SubtitleData? {
        return subtitleHelper.getAllSubtitles().firstOrNull { sub ->
            playerSelectedSubtitleTracks.any { (id, isSelected) ->
                isSelected && sub.getId() == id
            }
        }
    }

    override fun getAspectRatio(): Rational? {
        return exoPlayer?.videoFormat?.let { format ->
            Rational(format.width, format.height)
        }
    }

    override fun updateSubtitleStyle(style: SaveCaptionStyle) {
        subtitleHelper.setSubStyle(style)
    }

    override fun saveData() {
        Log.i(TAG, "saveData")
        updatedTime()

        exoPlayer?.let { exo ->
            playbackPosition = exo.currentPosition
            currentWindow = exo.currentMediaItemIndex
            isPlaying = exo.isPlaying
        }
    }

    private fun releasePlayer(saveTime: Boolean = true) {
        Log.i(TAG, "releasePlayer")
        eventLooperIndex += 1
        if (saveTime)
            updatedTime()

        currentTextRenderer = null
        currentSubtitleDecoder = null

        exoPlayer?.apply {
            playWhenReady = false

            // This may look weird, however on some TV devices the audio does not stop playing
            // so this may fix it?
            try {
                pause()
            } catch (t: Throwable) {
                // No documented exception, but just to be extra safe
                logError(t)
            }
            playerListener?.let {
                removeListener(it)
                playerListener = null
            }
            stop()
            release()
        }
        //simpleCache?.release()

        exoPlayer = null
        event(PlayerAttachedEvent(null))
        //simpleCache = null
    }

    override fun onStop() {
        Log.i(TAG, "onStop")

        saveData()
        if (!isAudioOnlyBackground) {
            handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        }
        //releasePlayer()
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        saveData()
        if (!isAudioOnlyBackground) {
            handleEvent(CSPlayerEvent.Pause, PlayerEventSource.Player)
        }
        //releasePlayer()
    }

    override fun onResume(context: Context) {
        isAudioOnlyBackground = false
        if (exoPlayer == null)
            reloadPlayer(context)
    }

    override fun release() {
        imageGenerator.release()
        releasePlayer()
    }

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        playBackSpeed = speed
    }

    companion object {
        /**
         * Setting this variable is permanent across app sessions.
         **/
        var preferredAudioTrackLanguage: String? = null
            get() {
                return field ?: getKey<String>(
                    "$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY",
                    field
                )?.also {
                    field = it
                }
            }
            set(value) {
                setKey("$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY", value)
                field = value
            }

        private var simpleCache: SimpleCache? = null

        /// Create a small factory for small things, no cache, no cronet
        private fun createOnlineSource(
            headers: Map<String, String>?,
            interceptor: Interceptor?
        ): HttpDataSource.Factory {
            val client = if (interceptor == null) {
                app.baseClient
            } else {
                app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
            }
            val source = OkHttpDataSource.Factory(client).setUserAgent(USER_AGENT)

            if (!headers.isNullOrEmpty()) {
                source.setDefaultRequestProperties(headers)
            }
            return source
        }

        private fun createVideoSource(
            link: ExtractorLink,
            interceptor: Interceptor?,
        ): HttpDataSource.Factory {
            val userAgent = link.headers.entries.find {
                it.key.equals("User-Agent", ignoreCase = true)
            }?.value ?: USER_AGENT

            val source = if (interceptor == null) {
                OkHttpDataSource.Factory(app.baseClient).setUserAgent(userAgent)
            } else {
                Log.d(TAG, "Using OkHttpDataSource for $link")
                val client = app.baseClient.newBuilder()
                    .addInterceptor(interceptor)
                    .build()
                OkHttpDataSource.Factory(client).setUserAgent(userAgent)
            }

            // Do no include empty referer, if the provider wants those they can use the header map.
            val refererMap =
                if (link.referer.isBlank()) emptyMap() else mapOf("referer" to link.referer)

            // These are extra headers the browser like to insert, not sure if we want to include them
            // for WIDEVINE/drm as well? Do that if someone gets 404 and creates an issue.
            val headers = refererMap + link.headers // Adds the headers from the provider, e.g Authorization

            return source.apply {
                setDefaultRequestProperties(headers)
            }
        }

        private fun Context.createOfflineSource(): DataSource.Factory {
            return DefaultDataSource.Factory(
                this,
                DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
            )
        }

        private fun getCache(context: Context, cacheSize: Long): SimpleCache? {
            return try {
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(
                    File(
                        context.cacheDir, "exoplayer"
                    ).also { it.deleteRecursively() }, // Ensures always fresh file
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    databaseProvider
                )
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        private fun getMediaItemBuilder(mimeType: String):
                MediaItem.Builder {
            return MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)
        }

        private fun getMediaItem(mimeType: String, uri: Uri): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(uri).build()
        }

        private fun getMediaItem(mimeType: String, url: String): MediaItem {
            return getMediaItemBuilder(mimeType).setUri(url).build()
        }

        private fun getTrackSelector(context: Context, maxVideoHeight: Int?): TrackSelector {
            val trackSelector = DefaultTrackSelector(context)
            trackSelector.parameters = trackSelector.buildUponParameters()
                // This will not force higher quality videos to fail
                // but will make the m3u8 pick the correct preferred
                .setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight ?: Int.MAX_VALUE)
                .setPreferredAudioLanguage(null)
                .build()
            return trackSelector
        }

        private var currentSubtitleDecoder: CustomSubtitleDecoderFactory? = null
        private var currentTextRenderer: TextRenderer? = null
    }

    private fun getCurrentTimestamp(writePosition: Long? = null): VideoSkipStamp? {
        val position = writePosition ?: this@CS3IPlayer.getPosition() ?: return null
        for (lastTimeStamp in lastTimeStamps) {
            if (lastTimeStamp.timestamp.startMs <= position && (position + (toleranceBeforeUs / 1000L) + 1) < lastTimeStamp.timestamp.endMs) {
                return lastTimeStamp
            }
        }
        return null
    }

    fun updatedTime(
        writePosition: Long? = null,
        source: PlayerEventSource = PlayerEventSource.Player
    ) {
        val position = writePosition ?: exoPlayer?.currentPosition

        getCurrentTimestamp(position)?.let { timestamp ->
            event(TimestampInvokedEvent(timestamp, source))
        }

        val duration = exoPlayer?.contentDuration
        if (duration != null && position != null) {
            event(
                PositionEvent(
                    source,
                    fromMs = exoPlayer?.currentPosition ?: 0,
                    position,
                    duration
                )
            )
        }
    }

    override fun seekTime(time: Long, source: PlayerEventSource) {
        exoPlayer?.seekTime(time, source)
    }

    override fun seekTo(time: Long, source: PlayerEventSource) {
        if (isMediaSeekable) {
            updatedTime(time, source)
            exoPlayer?.seekTo(time)
        } else {
            Log.i(TAG, "Media is not seekable, we can not seek to $time")
        }
    }

    private fun ExoPlayer.seekTime(time: Long, source: PlayerEventSource) {
        if (isMediaSeekable) {
            updatedTime(currentPosition + time, source)
            seekTo(currentPosition + time)
        } else {
            Log.i(TAG, "Media is not seekable, we can not seek to $time")
        }
    }

    override fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource) {
        Log.i(TAG, "handleEvent ${event.name}")
        try {
            exoPlayer?.apply {
                when (event) {
                    CSPlayerEvent.Play -> {
                        event(PlayEvent(source))
                        // If the player was stopped (e.g. notification dismissed) it lands in
                        // STATE_IDLE. A bare play() call is a no-op in that state, re-prepare and
                        // then resume to the current position once we are in STATE_READY again.
                        if (playbackState == Player.STATE_IDLE) {
                            val seekPosition = currentPosition
                            exoPlayer?.addListener(object : Player.Listener {
                                private var seekApplied = false
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (seekApplied || playbackState != Player.STATE_READY) return
                                    seekApplied = true
                                    exoPlayer?.seekTo(currentWindow, seekPosition)
                                    exoPlayer?.removeListener(this)
                                }
                            })
                            prepare()
                        }
                        play()
                    }

                    CSPlayerEvent.Pause -> {
                        event(PauseEvent(source))
                        pause()
                    }

                    CSPlayerEvent.ToggleMute -> {
                        if (volume <= 0) {
                            //is muted
                            volume = lastMuteVolume
                        } else {
                            // is not muted
                            lastMuteVolume = volume
                            volume = 0f
                        }
                    }

                    CSPlayerEvent.PlayPauseToggle -> {
                        if (isPlaying) {
                            handleEvent(CSPlayerEvent.Pause, source)
                        } else {
                            handleEvent(CSPlayerEvent.Play, source)
                        }
                    }

                    CSPlayerEvent.SeekForward -> seekTime(seekActionTime, source)

                    CSPlayerEvent.SeekBack -> seekTime(-seekActionTime, source)

                    CSPlayerEvent.Restart -> seekTo(0, source)

                    CSPlayerEvent.NextEpisode -> event(
                        EpisodeSeekEvent(
                            offset = 1,
                            source = source
                        )
                    )

                    CSPlayerEvent.PrevEpisode -> event(
                        EpisodeSeekEvent(
                            offset = -1,
                            source = source
                        )
                    )

                    CSPlayerEvent.SkipCurrentChapter -> {
                        //val dur = this@CS3IPlayer.getDuration() ?: return@apply
                        getCurrentTimestamp()?.let { lastTimeStamp ->
                            if (lastTimeStamp.skipToNextEpisode) {
                                handleEvent(CSPlayerEvent.NextEpisode, source)
                            } else {
                                seekTo(lastTimeStamp.timestamp.endMs + 1L)
                            }
                            event(TimestampSkippedEvent(timestamp = lastTimeStamp, source = source))
                        }
                    }

                    CSPlayerEvent.PlayAsAudio -> {
                        isAudioOnlyBackground = true
                        activity?.moveTaskToBack(false)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handleEvent error", t)
            event(ErrorEvent(t))
        }
    }

    private fun getSubSources(
        offlineSourceFactory: DataSource.Factory?,
        subHelper: PlayerSubtitleHelper,
        interceptor: Interceptor?,
    ): Pair<List<SingleSampleMediaSource>, List<SubtitleData>> {
        val activeSubtitles = ArrayList<SubtitleData>()
        val subSources = subHelper.getAllSubtitles().mapNotNull { sub ->
            val subConfig = MediaItem.SubtitleConfiguration.Builder(sub.getFixedUrl().toUri())
                .setMimeType(sub.mimeType)
                .setLanguage("_${sub.name}")
                .setId(sub.getId())
                .setSelectionFlags(0)
                .build()
            when (sub.origin) {
                SubtitleOrigin.DOWNLOADED_FILE, SubtitleOrigin.EMBEDDED_IN_VIDEO -> {
                    if (offlineSourceFactory != null) {
                        activeSubtitles.add(sub)
                        SingleSampleMediaSource.Factory(offlineSourceFactory)
                            .createMediaSource(subConfig, TIME_UNSET)
                    } else {
                        null
                    }
                }

                SubtitleOrigin.URL -> {
                    val dataSourceFactory = createOnlineSource(sub.headers, interceptor)
                    activeSubtitles.add(sub)
                    SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(subConfig, TIME_UNSET)
                }
            }
        }
        return Pair(subSources, activeSubtitles)
    }

    /**
     * Creates audio media sources from ExtractorLink's audioTracks
     * @param audioTracks List of audio tracks from ExtractorLink
     * @return List of MediaSource for audio tracks
     */
    private fun getAudioSources(
        audioTracks: List<AudioFile>,
        interceptor: Interceptor?,
    ): List<MediaSource> {
        return audioTracks.mapNotNull { audio ->
            try {
                val mediaItem = getMediaItem(MimeTypes.AUDIO_UNKNOWN, audio.url)
                val dataSourceFactory = createOnlineSource(audio.headers, interceptor)
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create audio source for ${audio.url}: ${e.message}")
                null
            }
        }
    }

    override fun isActive(): Boolean {
        return exoPlayer != null
    }

    @SuppressLint("UnsafeOptInUsageError")
    @MainThread
    private fun loadOnlinePlayer(context: Context, link: ExtractorLink, retry: Boolean = false) {
        Log.i(TAG, "loadOnlinePlayer $link")
        try {
            val mime = when (link.type) {
                ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                ExtractorLinkType.VIDEO -> MimeTypes.VIDEO_MP4
                ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET -> {
                    event(
                        ErrorEvent(
                            ErrorLoadingException(
                                context.getString(R.string.torrent_not_supported)
                            )
                        )
                    )
                    return
                }
            }

            currentLink = link

            if (ignoreSSL) {
                // Disables ssl check
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }


            val mediaItems = when (link) {
                is ExtractorLinkPlayList -> link.playlist.map {
                    MediaItemSlice(getMediaItem(mime, it.url), it.durationUs)
                }

                is DrmExtractorLink -> {
                    listOf(
                        // Single sliced list with unset length
                        MediaItemSlice(
                            getMediaItem(mime, link.url), Long.MIN_VALUE,
                            drm = DrmMetadata(
                                kid = link.kid,
                                key = link.key,
                                uuid = link.uuid.toJavaUuid(),
                                kty = link.kty,
                                licenseUrl = link.licenseUrl,
                                keyRequestParameters = link.keyRequestParameters,
                            )
                        )
                    )
                }

                else -> listOf(
                    // Single sliced list with unset length
                    MediaItemSlice(getMediaItem(mime, link.url), Long.MIN_VALUE)
                )
            }

            // For DASH or HLS single streams (non-playlist), prefer the player's default
            // live position instead of starting at 0. Use TIME_UNSET to let ExoPlayer pick
            // the live/default position when no explicit start position was provided.
            if (playbackPosition == 0L && (link.type == ExtractorLinkType.M3U8 || link.type == ExtractorLinkType.DASH)) {
                playbackPosition = TIME_UNSET
            }

            val provider = getApiFromNameNull(link.source)
            val interceptor: Interceptor? = provider?.getVideoInterceptor(link)

            val onlineSourceFactory =
                createVideoSource(
                    link = link,
                    interceptor = interceptor
                )

            val offlineSourceFactory = context.createOfflineSource()

            val (subSources, activeSubtitles) = getSubSources(
                offlineSourceFactory = offlineSourceFactory,
                subHelper = subtitleHelper,
                interceptor = interceptor, // Backwards compatibility, needs a new api to work properly
            )

            // Create audio sources from ExtractorLink's audioTracks
            val audioSources = getAudioSources(
                audioTracks = link.audioTracks,
                interceptor = interceptor, // Backwards compatibility, needs a new api to work properly
            )

            subtitleHelper.setActiveSubtitles(activeSubtitles.toSet())

            loadExo(
                context = context,
                mediaSlices = mediaItems,
                subSources = subSources,
                audioSources = audioSources,
                onlineSource = onlineSourceFactory
            )
        } catch (t: Throwable) {
            Log.e(TAG, "loadOnlinePlayer error", t)
            event(ErrorEvent(t))
        }
    }

    override fun reloadPlayer(context: Context) {
        Log.i(TAG, "reloadPlayer")

        releasePlayer(false)
        currentLink?.let {
            loadOnlinePlayer(context, it)
        } ?: currentDownloadedFile?.let {
            loadOfflinePlayer(context, it)
        }
    }

    private val tracksAnalyticsListener = object : AnalyticsListener {

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            event(TracksChangedEvent())
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            event(TracksChangedEvent())
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters
        ) {
            event(TracksChangedEvent())
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters
        ) {
            event(TracksChangedEvent())
        }
    }

}
