package com.kodastream.app.crash

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import com.kodastream.app.manga.reader.ReaderDiagnostics
import com.kodastream.app.manga.reader.asLine
import java.io.File

/**
 * Local-only crash capture. Nothing is uploaded: the report stays in
 * `filesDir/crash` until the owner copies or deletes it.
 */
object CrashRecorder {
    private const val FOLDER = "crash"
    private const val LAST_FILE = "last.txt"
    private const val MAX_FILES = 3
    private const val PREFS = "crash_recorder"
    private const val KEY_SEEN = "seen_report"

    @Volatile
    private var route: String? = null

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(app, previous))
        if (app is Application) {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    route = activity.localClassName
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
    }

    fun hasReport(context: Context): Boolean = lastFile(context).isFile

    /** The report text, with the process exit reason when Android keeps one. */
    fun readReport(context: Context): String? = runCatching {
        val text = lastFile(context).takeIf { it.isFile }?.readText() ?: return null
        val exit = exitReasonText(context)
        if (exit.isNullOrBlank()) text else "$text\nLast process exit: $exit\n"
    }.getOrNull()

    /** Returns a report that was not shown yet and marks it as seen. */
    fun takePendingReport(context: Context): String? = runCatching {
        val file = lastFile(context)
        if (!file.isFile) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_SEEN, -1L) == file.lastModified()) return null
        prefs.edit().putLong(KEY_SEEN, file.lastModified()).apply()
        readReport(context)
    }.getOrNull()

    fun deleteReports(context: Context) {
        runCatching {
            crashDir(context).listFiles()?.forEach { it.delete() }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SEEN)
                .apply()
        }
    }

    private fun writeCrash(app: Context, thread: Thread, error: Throwable) {
        val dir = crashDir(app)
        dir.mkdirs()
        val last = File(dir, LAST_FILE)
        if (last.isFile) {
            last.renameTo(File(dir, "crash-${last.lastModified()}.txt"))
        }
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val report = CrashReportData(
            timeMillis = System.currentTimeMillis(),
            versionName = packageInfo.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            threadName = thread.name,
            route = route,
            throwable = error,
            diagnostics = ReaderDiagnostics.recent(60).map { it.asLine() },
        )
        last.writeText(crashReportText(report))
        dir.listFiles()
            ?.filter { it.isFile && it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    private fun crashDir(context: Context): File = File(context.filesDir, FOLDER)

    private fun lastFile(context: Context): File = File(crashDir(context), LAST_FILE)

    private fun exitReasonText(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return null
            val exit = manager
                .getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .maxByOrNull { it.timestamp }
                ?: return null
            "${reasonLabel(exit.reason)} at ${formatCrashTime(exit.timestamp)}, " +
                "importance=${exit.importance}, status=${exit.status}, " +
                "description=${exit.description ?: "none"}"
        }.getOrNull()
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user request"
        ApplicationExitInfo.REASON_SIGNALED -> "signal"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package updated"
        else -> "other ($reason)"
    }

    private class CrashHandler(
        private val app: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching { writeCrash(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
