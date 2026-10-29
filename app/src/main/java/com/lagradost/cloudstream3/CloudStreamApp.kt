package com.lagradost.cloudstream3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.kodastream.app.BuildConfig
import com.kodastream.app.data.AppPrefs
import com.lagradost.api.setContext
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.utils.AppDebug
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.lang.ref.WeakReference

/**
 * The Application class. It keeps the exact name and the same static helpers as
 * CloudStream's CloudStreamApp, because extensions (.cs3 files) are linked
 * against `CloudStreamApp.getKey / setKey / context` at runtime.
 */
class CloudStreamApp : Application() {

    @OptIn(UnsafeSSL::class)
    override fun onCreate() {
        super.onCreate()
        AppDebug.isDebug = BuildConfig.DEBUG
        // Per-app language must be set before the first activity is created.
        AppPrefs.applyAppLanguage(this)
        // The network clients must exist before any extension runs.
        app.initClient(this)
        insecureApp.initClient(this, true)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = base
    }

    companion object {
        /** Use to get Activity from Context. */
        tailrec fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.getActivity()
                else -> null
            }
        }

        private var _context: WeakReference<Context>? = null
        var context: Context?
            get() = _context?.get()
            private set(value) {
                _context = WeakReference(value)
                setContext(value)
            }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }

        /** Opens the url in the user's browser. */
        fun openBrowser(url: String, activity: Activity?) {
            val ctx: Context = activity ?: context ?: return
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } catch (_: Throwable) {
            }
        }
    }
}
