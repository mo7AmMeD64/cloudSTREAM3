package com.kodastream.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodic safety net: favorites are also uploaded right after they change,
 * but a failed or skipped upload gets another chance every six hours.
 */
class FavoritesSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!SupabaseAuth.isSignedIn(context) || !SupabaseAuth.isCloudReady(context)) {
            return Result.success()
        }
        return when (FavoritesBackup.upload(context)) {
            UploadResult.Success -> Result.success()
            is UploadResult.Failed -> Result.retry()
        }
    }
}

object FavoritesSync {
    private const val WORK_NAME = "favorites_cloud_sync"
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Uploads shortly after any favorites change while an account is signed in. */
    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            LibraryStore.revision
                .drop(1)
                .debounce(5_000L)
                .collect {
                    if (SupabaseAuth.isSignedIn(appContext) &&
                        SupabaseAuth.isCloudReady(appContext)
                    ) {
                        FavoritesBackup.upload(appContext)
                    }
                }
        }
    }

    fun schedule(context: Context) {
        if (!SupabaseAuth.isSignedIn(context)) return
        val request = PeriodicWorkRequestBuilder<FavoritesSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
