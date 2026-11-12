// Ported from Mihon's data/download/DownloadWorker.kt (Apache-2.0). WorkManager
// keeps the chapter queue alive and gives it a foreground notification.
package com.kodastream.app.manga.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kodastream.app.R
import com.kodastream.app.manga.MangaPrefs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MangaDownloadWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val manager = MangaDownloadManager.get(appContext)
        manager.restoreQueue()

        var issue = networkIssue()
        if (issue != null) {
            manager.pause()
            return Result.retry()
        }

        if (!manager.startDirect()) {
            // Another worker may still own a running downloader; let it stop
            // before taking over instead of leaving the queue paused.
            return if (manager.isRunning.value) Result.retry() else Result.success()
        }

        try {
            // A background-started worker cannot always promote itself to a
            // foreground service; the download can still run without it.
            try {
                setForeground(foregroundInfo())
            } catch (_: IllegalStateException) {
            }
            while (issue == null && manager.isRunning.value) {
                delay(1_000)
                issue = networkIssue()
            }
        } finally {
            if (manager.isRunning.value && (issue != null || isStopped)) {
                withContext(NonCancellable) { manager.pause() }
            }
        }
        return if (issue != null) Result.retry() else Result.success()
    }

    private fun foregroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.manga_download_notification_channel))
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun networkIssue(): String? {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!online) {
            return applicationContext.getString(R.string.manga_download_no_network)
        }
        val wifiOnly = MangaPrefs.downloadOnlyOverWifi(applicationContext)
        val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (wifiOnly && !wifi) {
            return applicationContext.getString(R.string.manga_download_only_wifi)
        }
        return null
    }

    companion object {
        private const val TAG = "MangaDownloader"
        private const val CHANNEL = "manga_downloads"
        private const val FOREGROUND_ID = 1401

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (MangaPrefs.downloadOnlyOverWifi(context)) NetworkType.UNMETERED
                    else NetworkType.CONNECTED,
                )
                .build()
            val request = OneTimeWorkRequestBuilder<MangaDownloadWorker>()
                .addTag(TAG)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
