// Ported from Mihon's data/download/DownloadNotifier.kt (Apache-2.0), reduced to
// the progress, paused, complete and error notifications the queue screen uses.
package com.kodastream.app.manga.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kodastream.app.MainActivity
import com.kodastream.app.R

class MangaDownloadNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    private var lastProgressAt = 0L

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.manga_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    @Synchronized
    fun onProgress(download: MangaDownload) {
        val pages = download.pages ?: return
        if (pages.isEmpty()) return
        val done = download.downloadedImages
        val isComplete = done == pages.size
        val now = SystemClock.elapsedRealtime()
        if (!isComplete && now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        lastProgressAt = now
        manager.cancel(ID_PAUSED)

        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(download.manga.title)
            .setContentText(
                context.getString(R.string.manga_download_progress, download.chapter.name, done, pages.size),
            )
            .setProgress(pages.size, done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()
        notify(ID_PROGRESS, notification)
    }

    fun onPaused() {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle(context.getString(R.string.manga_download_paused))
            .setContentText(context.getString(R.string.manga_download_paused_summary))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()
        notify(ID_PAUSED, notification)
    }

    fun onComplete() {
        manager.cancel(ID_PROGRESS)
        manager.cancel(ID_PAUSED)
    }

    fun onError(message: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.manga_download_error))
            .setContentText(message ?: context.getString(R.string.manga_download_error_summary))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        notify(ID_ERROR, notification)
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: downloads continue unseen.
        }
    }

    private companion object {
        const val CHANNEL_PROGRESS = "manga_downloads"
        const val ID_PROGRESS = 1401
        const val ID_PAUSED = 1402
        const val ID_ERROR = 1403
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
