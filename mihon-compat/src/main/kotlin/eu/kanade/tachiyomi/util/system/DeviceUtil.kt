package eu.kanade.tachiyomi.util.system

import android.os.Build
import android.util.Log

/** The device checks Mihon's WebView interceptor relies on (Apache-2.0, from Mihon). */
object DeviceUtil {

    val isMiui: Boolean by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    val isSamsung: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    private fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            Log.w("KodaManga", "Unable to use SystemProperties.get()", e)
            null
        }
    }
}
