package com.kodastream.app.engine

import android.content.Context
import com.kodastream.app.data.AppPrefs
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the extensions that are already installed, once per process.
 *
 * It lives in its own scope instead of the Activity's, so closing the app
 * halfway through startup does not leave extensions half registered.
 */
object AppBootstrap {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        // Before any player reads the key; a user turning it off later sticks.
        AppPrefs.ensureHoldToSpeedUp(appContext)
        scope.launch {
            try {
                PluginManager.loadAllInstalled(appContext)
            } catch (t: Throwable) {
                logError(t)
            }
            if (AppPrefs.autoUpdate(appContext)) {
                try {
                    PluginManager.updateAll(appContext)
                } catch (t: Throwable) {
                    logError(t)
                }
            }
        }
    }
}
