package com.kodastream.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.engine.Engine
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every entry point must promote the service to foreground fast.
        promote()
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_PAUSE -> {
                active.remove(id)?.cancel()
                id?.let { DownloadStore.update(this, it) { d -> d.copy(state = DownloadStore.STATE_PAUSED) } }
            }

            ACTION_RESUME -> {
                id?.let {
                    DownloadStore.update(this, it) { d ->
                        d.copy(state = DownloadStore.STATE_QUEUED, error = null)
                    }
                }
                pump()
            }

            ACTION_CANCEL -> {
                active.remove(id)?.cancel()
                if (id != null) {
                    DownloadStore.find(this, id)?.let { d ->
                        DownloadLocations.delete(this, d.fileUri)
                        DownloadLocations.delete(this, d.posterUri)
                    }
                    DownloadStore.remove(this, id)
                }
            }

            ACTION_RETRY -> {
                id?.let {
                    DownloadStore.update(this, it) { d ->
                        d.copy(state = DownloadStore.STATE_QUEUED, error = null)
                    }
                }
                pump()
            }

            ACTION_PAUSE_ALL -> {
                active.keys.toList().forEach { key ->
                    active.remove(key)?.cancel()
                    DownloadStore.update(this, key) { d -> d.copy(state = DownloadStore.STATE_PAUSED) }
                }
            }

            ACTION_CANCEL_ALL -> {
                active.keys.toList().forEach { key -> active.remove(key)?.cancel() }
                DownloadStore.current(this)
                    .filter { it.state != DownloadStore.STATE_DONE }
                    .forEach { d ->
                        DownloadLocations.delete(this, d.fileUri)
                        DownloadLocations.delete(this, d.posterUri)
                        DownloadStore.remove(this, d.id)
                    }
            }

            // START and a sticky restart both fall through to the pump, which
            // re-adopts records left "downloading" by a killed service.
            else -> pump()
        }
        return START_STICKY
    }

    override fun onDestroy() {
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
        // A service restart adopts records stuck mid-download.
        DownloadStore.current(this)
            .filter { it.state == DownloadStore.STATE_DOWNLOADING && it.id !in active }
            .forEach { DownloadStore.update(this, it.id) { d -> d.copy(state = DownloadStore.STATE_QUEUED) } }

        val wifiOnly = AppPrefs.downloadWifiOnly(this)
        val max = AppPrefs.downloadMaxConcurrent(this)
        val onWifi = isOnWifi()

        DownloadStore.current(this)
            .filter { it.state == DownloadStore.STATE_QUEUED }
            .takeWhile { active.size < max && (!wifiOnly || onWifi) }
            .forEach { download ->
                active[download.id] = scope.launch {
                    try {
                        runDownload(download.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        DownloadStore.update(this@DownloadService, download.id) { d ->
                            d.copy(state = DownloadStore.STATE_FAILED, error = t.message)
                        }
                    } finally {
                        active.remove(download.id)
                        pump()
                    }
                }
            }

        updateNotification()
        if (active.isEmpty()) {
            val anyQueued = DownloadStore.current(this).any { it.state == DownloadStore.STATE_QUEUED }
            if (!anyQueued) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun runDownload(id: String) {
        val record = DownloadStore.find(this, id) ?: return
        DownloadStore.update(this, id) { it.copy(state = DownloadStore.STATE_DOWNLOADING, error = null) }

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
                DownloadStore.update(this, id) { it.copy(state = DownloadStore.STATE_DONE, error = null) }
                updateNotification()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotEnoughSpaceException) {
                DownloadStore.update(this, id) { d ->
                    d.copy(state = DownloadStore.STATE_FAILED, error = getString(R.string.download_error_space))
                }
                updateNotification()
                return
            } catch (t: Throwable) {
                attempt++
                if (attempt >= 5) {
                    DownloadStore.update(this, id) { d ->
                        d.copy(state = DownloadStore.STATE_FAILED, error = t.message ?: t.javaClass.simpleName)
                    }
                    updateNotification()
                    return
                }
                delay(5_000)
                // Re-read: the user may have paused or cancelled meanwhile.
                if (DownloadStore.find(this, id)?.state != DownloadStore.STATE_DOWNLOADING) return
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
        val uri = DownloadLocations.ensureTarget(this, record, fresh = !resume)
            ?: throw IOException("Cannot create the target file")
        val startOffset = if (resume) DownloadLocations.fileLength(this, uri) else 0L

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
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
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
            if (free in 0 until remaining) throw NotEnoughSpaceException()

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
                        DownloadStore.update(this, id) { d ->
                            d.copy(receivedBytes = received, totalBytes = total)
                        }
                        updateNotification()
                    }
                }
                output.flush()
            }
            DownloadStore.update(this, id) { d -> d.copy(receivedBytes = received, totalBytes = total) }
        }
    }

    private class Segment(val url: String, val key: Pair<String, String>?)

    /** An HLS stream: fetch the playlist, download every segment (AES-128 aware), append to one .ts file. */
    private fun downloadHls(id: String, job: Job?) {
        val record = DownloadStore.find(this, id) ?: return
        val resume = record.hlsSegment > 0
        val uri = DownloadLocations.ensureTarget(this, record, fresh = !resume)
            ?: throw IOException("Cannot create the target file")

        val playlistUrl = record.linkUrl
        var text = fetchText(playlistUrl, record)
        if (text.contains("#EXT-X-STREAM-INF")) {
            // Master playlist: follow the widest-bandwidth variant.
            val lines = text.lines().filter { it.isNotBlank() }
            var best: Pair<Long, String>? = null
            for (i in lines.indices) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val variant = lines.getOrNull(i + 1) ?: continue
                    if (variant.startsWith("#")) continue
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                    if (best == null || bandwidth > best.first) best = bandwidth to variant
                }
            }
            val variant = best?.second ?: throw IOException("No variant in the master playlist")
            text = fetchText(absolute(playlistUrl, variant), record)
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
                    currentKey = absolute(playlistUrl, keyUri) to iv
                }

                trimmed.isNotEmpty() && !trimmed.startsWith("#") ->
                    segments.add(Segment(absolute(playlistUrl, trimmed), currentKey))
            }
        }
        if (segments.isEmpty()) throw IOException("No segments in the playlist")

        if (DownloadLocations.freeBytes(this) in 0 until 50L * 1024 * 1024) throw NotEnoughSpaceException()

        DownloadStore.update(this, id) { d -> d.copy(fileUri = uri, hlsTotal = segments.size) }

        var index = if (resume) record.hlsSegment else 0
        DownloadLocations.openOutput(this, uri, append = resume).use { output ->
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
                DownloadStore.update(this, id) { d ->
                    d.copy(hlsSegment = index, receivedBytes = d.receivedBytes + bytes.size)
                }
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
        return manager.getNetworkCapabilities(network)?.hasTransport(
            android.net.NetworkCapabilities.TRANSPORT_WIFI,
        ) == true
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

        if (running.isEmpty()) {
            builder.setContentText(getString(R.string.download_queued))
        } else {
            val first = running.first()
            val fraction = first.progressFraction
            builder.setContentTitle(first.title)
                .setProgress(100, (fraction.coerceAtLeast(0f) * 100).toInt(), fraction < 0f)
                .setContentText(getString(R.string.download_notification_count, running.size + queued))
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
