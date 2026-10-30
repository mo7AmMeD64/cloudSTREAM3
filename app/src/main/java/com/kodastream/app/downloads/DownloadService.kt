package com.kodastream.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.engine.Engine
import android.util.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.HlsPlaylistParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Duration
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The download engine: a small dataSync foreground service that keeps
 * running while the app is closed. Concurrency, Wi-Fi gating, pause/resume
 * (HTTP Range for direct files, segment index for HLS), retry with backoff,
 * AES-128 HLS support and progress notifications all live here. State is
 * persisted through [DownloadStore] after every step, so anything can be
 * interrupted - network drop, app kill, reboot - and continued later.
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 10

        const val ACTION_START = "com.kodastream.app.downloads.START"
        const val ACTION_PAUSE = "com.kodastream.app.downloads.PAUSE"
        const val ACTION_RESUME = "com.kodastream.app.downloads.RESUME"
        const val ACTION_CANCEL = "com.kodastream.app.downloads.CANCEL"
        const val ACTION_RETRY = "com.kodastream.app.downloads.RETRY"
        const val ACTION_PAUSE_ALL = "com.kodastream.app.downloads.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.kodastream.app.downloads.CANCEL_ALL"
        const val EXTRA_ID = "id"

        /** Kicks the service: starts anything queued and re-adopts interrupted work. */
        fun ensure(context: Context) = send(context, ACTION_START, null)
        fun pause(context: Context, id: String) = send(context, ACTION_PAUSE, id)
        fun resume(context: Context, id: String) = send(context, ACTION_RESUME, id)
        fun cancel(context: Context, id: String) = send(context, ACTION_CANCEL, id)
        fun retry(context: Context, id: String) = send(context, ACTION_RETRY, id)

        private fun send(context: Context, action: String, id: String?) {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            if (id != null) intent.putExtra(EXTRA_ID, id)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = HashMap<String, Job>()
    private lateinit var notifications: NotificationManager
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "networkCallback.onAvailable: network=$network")
            val wifiOnly = AppPrefs.downloadWifiOnly(this@DownloadService)
            val caps = connectivityManager?.getNetworkCapabilities(network)
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (shouldTriggerPumpOnNetworkEvent(wifiOnly, hasWifi)) {
                pump()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            Log.d(
                TAG,
                "networkCallback.onCapabilitiesChanged: hasWifi=$hasWifi",
            )
            val wifiOnly = AppPrefs.downloadWifiOnly(this@DownloadService)
            if (shouldTriggerPumpOnNetworkEvent(wifiOnly, hasWifi)) {
                pump()
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "networkCallback.onLost: network=$network")
            pump()
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPrefs.KEY_DL_WIFI || key == AppPrefs.KEY_DL_MAX) {
            Log.d(TAG, "prefListener changed key=$key -> triggering pump()")
            pump()
        }
    }

    /**
     * The same client the player would play this link through: the engine's
     * base client plus the provider's own URL-signing interceptor. A bare
     * client gets 403s on providers that sign their video requests.
     */
    private fun clientFor(record: DownloadStore.Download): OkHttpClient {
        val api = Engine.provider(record.apiName)
        val link = ExtractorLink(
            source = record.apiName,
            name = record.linkName,
            url = record.linkUrl,
            referer = record.headers["referer"] ?: "",
            quality = record.quality,
            headers = record.headers,
            type = if (record.isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        )
        return app.baseClient.newBuilder()
            .apply {
                api?.getVideoInterceptor(link)?.let { addInterceptor(it) }
                connectTimeout(Duration.ofSeconds(30))
                readTimeout(Duration.ofSeconds(60))
            }
            .build()
    }

    /** Providers sometimes hand out header maps okhttp refuses; clean them. */
    private fun safeHeaders(headers: Map<String, String>): Map<String, String> =
        headers
            .filterKeys { it.isNotBlank() && !it.contains(':') }
            .mapValues { (_, value) -> value.replace(Regex("[\\r\\n]"), "") }

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notifications.createNotificationChannel(channel)

        connectivityManager = getSystemService(ConnectivityManager::class.java)
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (e: Exception) {
            logWarning(TAG, "Failed to register default network callback: ${e.message}")
            try {
                val request = NetworkRequest.Builder().build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
                networkCallbackRegistered = true
            } catch (e2: Exception) {
                logWarning(TAG, "Failed to register network callback: ${e2.message}")
            }
        }

        AppPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every entry point must promote the service to foreground fast.
        promote()
        val id = intent?.getStringExtra(EXTRA_ID)
        Log.d(TAG, "onStartCommand action=${intent?.action}, id=$id")
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (id != null) {
                    DownloadStore.updateState(this, id, DownloadStore.STATE_PAUSED)
                    val job = synchronized(active) { active.remove(id) }
                    job?.cancel()
                }
                pump()
            }

            ACTION_RESUME -> {
                id?.let {
                    DownloadStore.updateState(this, it, DownloadStore.STATE_QUEUED)
                }
                pump()
            }

            ACTION_CANCEL -> {
                if (id != null) {
                    val job = synchronized(active) { active.remove(id) }
                    job?.cancel()
                    DownloadStore.find(this, id)?.let { d ->
                        DownloadLocations.delete(this, d.fileUri)
                        DownloadLocations.delete(this, d.posterUri)
                    }
                    DownloadStore.remove(this, id)
                }
                pump()
            }

            ACTION_RETRY -> {
                id?.let {
                    DownloadStore.updateState(this, it, DownloadStore.STATE_QUEUED)
                }
                pump()
            }

            ACTION_PAUSE_ALL -> {
                val jobs = synchronized(active) {
                    val list = active.values.toList()
                    active.clear()
                    list
                }
                jobs.forEach { it.cancel() }
                DownloadStore.current(this)
                    .filter { it.state == DownloadStore.STATE_DOWNLOADING || it.state == DownloadStore.STATE_QUEUED }
                    .forEach { DownloadStore.updateState(this, it.id, DownloadStore.STATE_PAUSED) }
                pump()
            }

            ACTION_CANCEL_ALL -> {
                val jobs = synchronized(active) {
                    val list = active.values.toList()
                    active.clear()
                    list
                }
                jobs.forEach { it.cancel() }
                DownloadStore.current(this)
                    .filter { it.state != DownloadStore.STATE_DONE }
                    .forEach { d ->
                        DownloadLocations.delete(this, d.fileUri)
                        DownloadLocations.delete(this, d.posterUri)
                        DownloadStore.remove(this, d.id)
                    }
                pump()
            }

            // START and a sticky restart both fall through to the pump, which
            // re-adopts records left "downloading" by a killed service.
            else -> pump()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        DownloadStore.flush(this)
        AppPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        if (networkCallbackRegistered) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
            networkCallbackRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promote() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Starts queued work up to the concurrency limit, then stops when idle. */
    private fun pump() {
        synchronized(active) {
            // A service restart adopts records stuck mid-download.
            DownloadStore.current(this)
                .filter { it.state == DownloadStore.STATE_DOWNLOADING && it.id !in active }
                .forEach { DownloadStore.updateState(this, it.id, DownloadStore.STATE_QUEUED) }

            val wifiOnly = AppPrefs.downloadWifiOnly(this)
            val max = AppPrefs.downloadMaxConcurrent(this)
            val onWifi = isOnWifi()
            val queuedItems = DownloadStore.current(this).filter { it.state == DownloadStore.STATE_QUEUED }

            Log.d(
                TAG,
                "pump(): active=${active.size}, max=$max, wifiOnly=$wifiOnly, onWifi=$onWifi, queued=${queuedItems.size}",
            )

            if (wifiOnly && !onWifi) {
                if (active.isNotEmpty()) {
                    Log.d(TAG, "pump(): cancelling active downloads because Wi-Fi only is required and Wi-Fi is lost")
                    active.values.toList().forEach { it.cancel() }
                }
                updateNotification()
                return
            }

            val availableSlots = calculateAvailableSlots(max, active.size)
            if (availableSlots > 0) {
                val toStart = selectQueuedCandidates(
                    queued = queuedItems,
                    activeIds = active.keys,
                    availableSlots = availableSlots,
                )

                for (download in toStart) {
                    Log.d(TAG, "pump(): starting queued download id=${download.id}")
                    DownloadStore.updateState(this, download.id, DownloadStore.STATE_DOWNLOADING)
                    active[download.id] = scope.launch {
                        try {
                            runDownload(download.id)
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Download cancelled for ${download.id}")
                            throw e
                        } catch (t: Throwable) {
                            Log.e(TAG, "Download failed for ${download.id}: ${t.message}", t)
                            DownloadStore.updateState(this@DownloadService, download.id, DownloadStore.STATE_FAILED, t.message)
                        } finally {
                            synchronized(active) {
                                active.remove(download.id)
                            }
                            pump()
                        }
                    }
                }
            }

            val anyQueued = DownloadStore.current(this).any { it.state == DownloadStore.STATE_QUEUED }
            if (active.isEmpty() && !anyQueued) {
                Log.d(TAG, "pump(): idle and no queued items, stopping foreground service")
                DownloadStore.flush(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (::notifications.isInitialized) {
                    notifications.cancel(NOTIFICATION_ID)
                }
                stopSelf()
            } else {
                updateNotification()
            }
        }
    }

    private suspend fun runDownload(id: String) {
        val record = DownloadStore.find(this, id) ?: run {
            Log.d(TAG, "runDownload(): record not found for id=$id")
            return
        }
        Log.d(TAG, "runDownload(): starting id=$id, type=${record.streamType}")
        DownloadStore.updateState(this, id, DownloadStore.STATE_DOWNLOADING)

        // One folder per title: resolved once (reusing a sibling episode's
        // folder when there is one) and kept on the record from then on.
        DownloadStore.find(this, id)?.let { current ->
            if (current.folderUri == null) {
                DownloadLocations.resolveFolder(this, current)?.let { folder ->
                    DownloadStore.update(this, id) { it.copy(folderUri = folder) }
                }
            }
        }

        // The worker checks this between reads: pause and cancel must not wait
        // for a blocking socket to time out.
        val job = currentCoroutineContext()[Job]

        // Transient failures retry quietly; the user only sees "failed" after
        // several attempts. State changes made meanwhile end the run.
        var attempt = 0
        while (true) {
            try {
                coroutineScope {
                    if (record.isHls) downloadHls(id, job)
                    else downloadDirect(id, job)
                }
                if (record.subUrl != null && DownloadStore.find(this, id)?.subtitleUri == null) {
                    downloadSubtitle(id)
                }
                if (record.posterUrl != null && DownloadStore.find(this, id)?.posterUri == null) {
                    downloadPoster(id)
                }
                Log.d(TAG, "runDownload(): completed successfully id=$id")
                DownloadStore.updateState(this, id, DownloadStore.STATE_DONE)
                updateNotification()
                return
            } catch (e: CancellationException) {
                Log.d(TAG, "runDownload(): cancelled id=$id")
                throw e
            } catch (e: NotEnoughSpaceException) {
                Log.e(TAG, "runDownload(): not enough space for id=$id", e)
                DownloadStore.updateState(this, id, DownloadStore.STATE_FAILED, getString(R.string.download_error_space))
                updateNotification()
                return
            } catch (t: Throwable) {
                Log.w(TAG, "runDownload(): attempt $attempt failed for id=$id: ${t.message}")
                if (AppPrefs.downloadWifiOnly(this) && !isOnWifi()) {
                    Log.d(TAG, "runDownload(): Wi-Fi lost for id=$id, re-queuing")
                    DownloadStore.updateState(this, id, DownloadStore.STATE_QUEUED)
                    updateNotification()
                    return
                }
                attempt++
                if (attempt >= 5) {
                    Log.e(TAG, "runDownload(): max attempts reached for id=$id", t)
                    DownloadStore.updateState(this, id, DownloadStore.STATE_FAILED, t.message ?: t.javaClass.simpleName)
                    updateNotification()
                    return
                }
                delay(5_000)
                // Re-read: the user may have paused or cancelled meanwhile.
                if (DownloadStore.find(this, id)?.state != DownloadStore.STATE_DOWNLOADING) {
                    Log.d(TAG, "runDownload(): state changed while backing off for id=$id, aborting")
                    return
                }
            }
        }
    }

    private class NotEnoughSpaceException : IOException("Not enough free space")

    private fun checkAlive(job: Job?) {
        if (job != null && !job.isActive) throw CancellationException("download stopped")
    }

    /** One file of bytes: streams to the target and resumes with HTTP Range. */
    private fun downloadDirect(id: String, job: Job?) {
        val record = DownloadStore.find(this, id) ?: return
        val resume = record.receivedBytes > 0
        var uri = DownloadLocations.ensureTarget(this, record, fresh = !resume)
            ?: throw IOException("Cannot create the target file")
        var startOffset = if (resume) DownloadLocations.fileLength(this, uri) else 0L

        val headers = safeHeaders(record.headers)
        val request = Request.Builder()
            .url(record.linkUrl)
            .headers(headers.toHeaders())
            .apply {
                if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
                    header("User-Agent", USER_AGENT)
                }
                if (startOffset > 0) header("Range", "bytes=$startOffset-")
            }
            .build()

        clientFor(record).newCall(request).execute().use { response ->
            val decision = resumeDecision(startOffset, response.code)
            if (decision == ResumeAction.ERROR) throw IOException("HTTP ${response.code}")

            if (decision == ResumeAction.RESTART_FROM_ZERO) {
                logWarning("DownloadService", "Server ignored Range, restarting from 0")
                DownloadLocations.delete(this, uri)
                uri = DownloadLocations.ensureTarget(this, record, fresh = true)
                    ?: throw IOException("Cannot create the target file")
                startOffset = 0L
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val partial = response.code == 206
            val total = when {
                partial && contentLength >= 0 -> startOffset + contentLength
                contentLength >= 0 -> contentLength
                record.totalBytes > 0 -> record.totalBytes
                else -> 0L
            }

            // Enough room? When the size is unknown, demand a 100MB cushion.
            val free = DownloadLocations.freeBytes(this)
            val remaining = if (total > 0) total - startOffset else 100L * 1024 * 1024
            if (DownloadLocations.shouldBlockForSpace(free, remaining)) throw NotEnoughSpaceException()

            DownloadStore.update(this, id) { d ->
                d.copy(fileUri = uri, receivedBytes = startOffset, totalBytes = total)
            }

            val input = response.body?.byteStream() ?: throw IOException("Empty body")
            var received = startOffset
            var lastPublish = 0L
            DownloadLocations.openOutput(this, uri, append = startOffset > 0).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkAlive(job)
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    received += read
                    val now = System.currentTimeMillis()
                    if (now - lastPublish > 900) {
                        lastPublish = now
                        DownloadStore.updateProgress(this, id, received, total)
                        updateNotification()
                    }
                }
                output.flush()
            }
            DownloadStore.updateProgress(this, id, received, total)
        }
    }

    private class Segment(val url: String, val key: Pair<String, String>?)

    /** An HLS stream: fetch the playlist, download every segment (AES-128 aware), append to one .ts file. */
    private fun downloadHls(id: String, job: Job?) {
        val record = DownloadStore.find(this, id) ?: return
        val resume = record.hlsSegment > 0 || record.hlsInitWritten
        val uri = DownloadLocations.ensureTarget(this, record, fresh = !resume)
            ?: throw IOException("Cannot create the target file")

        val playlistUrl = record.linkUrl
        var text = fetchText(playlistUrl, record)
        var mediaPlaylistUrl = playlistUrl
        if (text.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = selectDownloadVariant(text, playlistUrl)
                ?: throw IOException("No variant in the master playlist")
            mediaPlaylistUrl = variantUrl
            text = fetchText(variantUrl, record)
        }

        var mediaSequence = 0L
        var currentKey: Pair<String, String>? = null
        val segments = ArrayList<Segment>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE") ->
                    mediaSequence = trimmed.substringAfter(":").toLongOrNull() ?: 0

                trimmed.startsWith("#EXT-X-KEY") -> {
                    if (!trimmed.contains("METHOD=AES-128")) {
                        throw IOException(getString(R.string.download_unsupported))
                    }
                    val keyUri = Regex("URI=\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1) ?: ""
                    val iv = Regex("IV=0[xX]([0-9a-fA-F]+)").find(trimmed)?.groupValues?.get(1) ?: ""
                    currentKey = absolute(mediaPlaylistUrl, keyUri) to iv
                }

                trimmed.isNotEmpty() && !trimmed.startsWith("#") ->
                    segments.add(Segment(absolute(mediaPlaylistUrl, trimmed), currentKey))
            }
        }
        if (segments.isEmpty()) throw IOException("No segments in the playlist")

        val mapUri = extractMapUri(text)
        val initUrl = mapUri?.let { absolute(mediaPlaylistUrl, it) }

        val free = DownloadLocations.freeBytes(this)
        if (DownloadLocations.shouldBlockForSpace(free, 50L * 1024 * 1024)) throw NotEnoughSpaceException()

        DownloadStore.update(this, id) { d -> d.copy(fileUri = uri, hlsTotal = segments.size) }

        var index = if (record.hlsSegment > 0) record.hlsSegment else 0
        var receivedBytes = record.receivedBytes
        DownloadLocations.openOutput(this, uri, append = resume).use { output ->
            if (initUrl != null && !record.hlsInitWritten && record.hlsSegment == 0) {
                checkAlive(job)
                val initBytes = fetchBytes(initUrl, record)
                output.write(initBytes)
                output.flush()
                receivedBytes += initBytes.size
                DownloadStore.updateProgress(
                    context = this,
                    id = id,
                    bytes = receivedBytes,
                    total = 0L,
                    hlsInitWritten = true,
                )
            }

            while (index < segments.size) {
                checkAlive(job)
                val segment = segments[index]
                var bytes = fetchBytes(segment.url, record)
                segment.key?.let { (keyUrl, ivHex) ->
                    val keyBytes = fetchBytes(keyUrl, record)
                    val iv = if (ivHex.isNotBlank()) {
                        ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    } else {
                        // Default IV: the media sequence number, big endian.
                        ByteArray(16).also { buffer ->
                            var n = mediaSequence + index
                            for (i in 15 downTo 0) {
                                buffer[i] = (n and 0xFF).toByte()
                                n = n shr 8
                            }
                        }
                    }
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                    bytes = cipher.doFinal(bytes)
                }
                output.write(bytes)
                index++
                receivedBytes += bytes.size
                DownloadStore.updateProgress(
                    context = this,
                    id = id,
                    bytes = receivedBytes,
                    total = 0L,
                    hlsSegment = index,
                )
                updateNotification()
            }
            output.flush()
        }
    }

    /** Saves the provider's subtitle beside the video, same folder, same name. */
    private fun downloadSubtitle(id: String) {
        val record = DownloadStore.find(this, id) ?: return
        try {
            val subUrl = record.subUrl ?: return
            val bytes = fetchBytes(subUrl, record)
            val ext = subUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
            val safeExt = if (ext in setOf("srt", "ass", "ssa", "vtt", "ttml")) ext else "vtt"
            val mime = when (safeExt) {
                "srt" -> "application/x-subrip"
                "ass", "ssa" -> "text/x-ssa"
                "ttml" -> "application/ttml+xml"
                else -> "text/vtt"
            }
            // "<video base>.<language>.<ext>", next to its video.
            val langSuffix = record.subLang
                ?.replace(Regex("[^A-Za-z0-9]"), "")
                ?.lowercase()
                ?.take(8)
                ?.ifBlank { null }
                ?: "sub"
            val subName = record.fileName.substringBeforeLast('.') + "." + langSuffix + "." + safeExt
            val folder = record.folderUri ?: DownloadLocations.resolveFolder(this, record) ?: return

            val uri: String? = if (folder.startsWith("content://")) {
                // fromSingleUri cannot create files - resolve through the tree.
                DownloadLocations.createFileInFolder(this, folder, subName, mime)?.also { created ->
                    DownloadLocations.openOutput(this, created, append = false).use { it.write(bytes) }
                }
            } else {
                val dir = File(Uri.parse(folder).path!!)
                dir.mkdirs()
                val file = File(dir, subName)
                file.delete()
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            }
            if (uri != null) {
                DownloadStore.update(this, id) { it.copy(subtitleUri = uri) }
            }
        } catch (_: Throwable) {
            // A missing subtitle never fails the video download.
        }
    }

    private fun downloadPoster(id: String) {
        val record = DownloadStore.find(this, id) ?: return
        try {
            val bytes = fetchBytes(record.posterUrl ?: return, record)
            // Thumbnails live in the app's own storage; the user's download
            // folder holds only video files.
            val dir = File(filesDir, "download_posters").apply { mkdirs() }
            val file = File(dir, "${record.id.hashCode()}.jpg")
            file.writeBytes(bytes)
            DownloadStore.update(this, id) { it.copy(posterUri = Uri.fromFile(file).toString()) }
        } catch (_: Throwable) {
        }
    }

    // ---- small helpers ----

    private fun requestFor(url: String, http: OkHttpClient, headers: Map<String, String>): Request =
        Request.Builder()
            .url(url)
            .headers(safeHeaders(headers).toHeaders())
            .apply {
                if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
                    header("User-Agent", USER_AGENT)
                }
            }
            .build()

    private fun fetchText(url: String, record: DownloadStore.Download): String =
        clientFor(record).newCall(requestFor(url, clientFor(record), record.headers)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty body")
        }

    private fun fetchBytes(url: String, record: DownloadStore.Download): ByteArray =
        clientFor(record).newCall(requestFor(url, clientFor(record), record.headers)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty body")
        }

    private fun absolute(base: String, relative: String): String = try {
        URI(base).resolve(relative).toString()
    } catch (_: Throwable) {
        relative
    }

    private fun isOnWifi(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return manager.allNetworks.any { net ->
                val netCaps = manager.getNetworkCapabilities(net)
                netCaps != null &&
                    netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return false
    }

    private fun actionPending(action: String, titleRes: Int, icon: Int): NotificationCompat.Action =
        NotificationCompat.Action(
            icon,
            getString(titleRes),
            PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, DownloadService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

    private fun buildNotification(): Notification {
        val all = DownloadStore.current(this)
        val running = all.filter { it.state == DownloadStore.STATE_DOWNLOADING }
        val queued = all.count { it.state == DownloadStore.STATE_QUEUED }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentTitle(getString(R.string.download_notification_channel))

        val wifiOnly = AppPrefs.downloadWifiOnly(this)
        val onWifi = isOnWifi()
        val notifState = resolveDownloadNotificationState(
            hasRunning = running.isNotEmpty(),
            hasQueued = queued > 0,
            wifiOnly = wifiOnly,
            onWifi = onWifi,
        )

        when (notifState) {
            DownloadNotificationState.WAITING_FOR_WIFI -> {
                val first = running.firstOrNull() ?: all.firstOrNull { it.state == DownloadStore.STATE_QUEUED }
                if (first != null) {
                    builder.setContentTitle(first.title)
                }
                builder.setContentText(getString(R.string.download_waiting_wifi))
            }
            DownloadNotificationState.DOWNLOADING -> {
                val first = running.first()
                val fraction = first.progressFraction
                builder.setContentTitle(first.title)
                    .setProgress(100, (fraction.coerceAtLeast(0f) * 100).toInt(), fraction < 0f)
                    .setContentText(getString(R.string.download_notification_count, running.size + queued))
            }
            DownloadNotificationState.QUEUED,
            DownloadNotificationState.IDLE -> {
                builder.setContentText(getString(R.string.download_queued))
            }
        }

        if (running.isNotEmpty() || queued > 0) {
            builder.addAction(
                actionPending(
                    ACTION_PAUSE_ALL,
                    R.string.download_notification_pause_all,
                    android.R.drawable.ic_media_pause,
                ),
            )
            builder.addAction(
                actionPending(
                    ACTION_CANCEL_ALL,
                    R.string.download_notification_cancel_all,
                    android.R.drawable.ic_delete,
                ),
            )
        }
        return builder.build()
    }

    private fun updateNotification() {
        try {
            notifications.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: the work continues, unseen.
        }
    }
}

enum class ResumeAction {
    APPEND,
    RESTART_FROM_ZERO,
    ERROR
}

/**
 * Pure helper for deciding how to resume or start a direct download based on the
 * initial file offset and the server's HTTP response code.
 */
internal fun resumeDecision(startOffset: Long, responseCode: Int): ResumeAction {
    if (startOffset < 0L) return ResumeAction.ERROR
    return when {
        startOffset == 0L && (responseCode == 200 || responseCode == 206) -> ResumeAction.APPEND
        startOffset > 0L && responseCode == 206 -> ResumeAction.APPEND
        startOffset > 0L && responseCode == 200 -> ResumeAction.RESTART_FROM_ZERO
        else -> ResumeAction.ERROR
    }
}

/**
 * Extracts the URI attribute from the first #EXT-X-MAP tag in the playlist.
 * Used for fMP4 HLS streams that require an initialization segment before media segments.
 * Returns null if no #EXT-X-MAP tag or valid URI attribute is found.
 */
internal fun extractMapUri(playlistText: String): String? {
    for (line in playlistText.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("#EXT-X-MAP")) {
            val uri = Regex("""\bURI\s*=\s*"([^"]+)"""").find(trimmed)?.groupValues?.get(1)
            if (!uri.isNullOrEmpty()) {
                return uri
            }
        }
    }
    return null
}

/**
 * Pure helper for selecting the best HLS variant to download.
 * Filters for standalone-playable variants (declares or muxes audio, non-trickplay),
 * selecting the highest resolution tie-broken by averageBitrate (then peakBitrate).
 * If no standalone variant is found, falls back to the highest bandwidth variant and logs a warning.
 * Returns null if the playlist is not a master playlist.
 */
internal fun selectDownloadVariant(playlistText: String, baseUrl: String): String? {
    val parsed = try {
        HlsPlaylistParser.parse(baseUrl, playlistText)
    } catch (_: Throwable) {
        null
    }

    if (parsed != null && parsed.variants.isNotEmpty()) {
        val standalone = parsed.variants.filter { it.isPlayableStandalone(parsed) }
        val chosen = if (standalone.isNotEmpty()) {
            standalone.maxWithOrNull(
                compareBy<HlsPlaylistParser.Variant>(
                    { v ->
                        if (v.format.width > 0 && v.format.height > 0) {
                            v.format.width.toLong() * v.format.height.toLong()
                        } else {
                            0L
                        }
                    },
                    { it.format.averageBitrate },
                    { it.format.peakBitrate }
                )
            )
        } else {
            logWarning("DownloadService", "No standalone-playable HLS variant found; falling back to highest bandwidth")
            parsed.variants.maxByOrNull { it.format.peakBitrate }
        }
        return chosen?.let { extractVariantUrl(it) }
    }

    if (playlistText.contains("#EXT-X-STREAM-INF")) {
        logWarning("DownloadService", "HlsPlaylistParser found no variants; falling back to regex highest bandwidth")
        val lines = playlistText.lines().filter { it.isNotBlank() }
        var best: Pair<Long, String>? = null
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val variant = lines.getOrNull(i + 1) ?: continue
                if (variant.startsWith("#")) continue
                val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                if (best == null || bandwidth > best.first) best = bandwidth to variant
            }
        }
        return best?.second?.let {
            try {
                URI(baseUrl).resolve(it).toString()
            } catch (_: Throwable) {
                it
            }
        }
    }

    return null
}

private fun logWarning(tag: String, message: String) {
    try {
        Log.w(tag, message)
    } catch (_: Throwable) {
        println("WARNING $tag: $message")
    }
}

private fun extractVariantUrl(variant: HlsPlaylistParser.Variant): String? {
    try {
        val method = variant.javaClass.methods.firstOrNull { it.name == "getUrl" }
        val result = method?.invoke(variant)?.toString()
        if (!result.isNullOrBlank()) return result
    } catch (_: Throwable) {
    }
    return Regex("""url=([^,\)]+)""").find(variant.toString())?.groupValues?.get(1)
}

enum class DownloadNotificationState {
    IDLE,
    WAITING_FOR_WIFI,
    DOWNLOADING,
    QUEUED,
}

/**
 * Pure helper to resolve the foreground notification display state based on running/queued counts
 * and Wi-Fi gating conditions.
 */
internal fun resolveDownloadNotificationState(
    hasRunning: Boolean,
    hasQueued: Boolean,
    wifiOnly: Boolean,
    onWifi: Boolean,
): DownloadNotificationState {
    if (wifiOnly && !onWifi && (hasRunning || hasQueued)) {
        return DownloadNotificationState.WAITING_FOR_WIFI
    }
    if (hasRunning) {
        return DownloadNotificationState.DOWNLOADING
    }
    if (hasQueued) {
        return DownloadNotificationState.QUEUED
    }
    return DownloadNotificationState.IDLE
}

/**
 * Pure helper to check if downloads should be blocked waiting for Wi-Fi.
 */
internal fun shouldBlockForWifi(wifiOnly: Boolean, onWifi: Boolean): Boolean =
    wifiOnly && !onWifi

/**
 * Pure helper deciding if a network availability/capability change event should trigger pump().
 * If wifiOnly is true, only networks with TRANSPORT_WIFI trigger pump().
 * If wifiOnly is false, any active network event triggers pump().
 */
internal fun shouldTriggerPumpOnNetworkEvent(wifiOnly: Boolean, hasWifi: Boolean): Boolean =
    !wifiOnly || hasWifi

/**
 * Pure helper calculating how many downloads can be started given the concurrency limit
 * and currently active job count.
 */
internal fun calculateAvailableSlots(maxConcurrent: Int, activeCount: Int): Int =
    (maxConcurrent - activeCount).coerceAtLeast(0)

/**
 * Pure helper filtering queued downloads to pick candidates ready to start:
 * only items with STATE_QUEUED and not already in the active jobs set,
 * limited to availableSlots.
 */
internal fun selectQueuedCandidates(
    queued: List<DownloadStore.Download>,
    activeIds: Set<String>,
    availableSlots: Int,
): List<DownloadStore.Download> {
    if (availableSlots <= 0) return emptyList()
    return queued
        .filter { it.state == DownloadStore.STATE_QUEUED && it.id !in activeIds }
        .take(availableSlots)
}


