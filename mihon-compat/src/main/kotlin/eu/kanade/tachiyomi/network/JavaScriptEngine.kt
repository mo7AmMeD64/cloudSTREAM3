package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Util for evaluating JavaScript in sources (Apache-2.0, from Mihon).
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(context: Context) {

    @Suppress("unused")
    private val applicationContext = context.applicationContext

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * @since extensions-lib 1.4
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        try {
            QuickJs.create().use {
                it.evaluate(script) as T
            }
        } catch (e: Throwable) {
            if (e is UnsupportedOperationException) throw e
            throw UnsupportedOperationException("QuickJS is not available on this device", e)
        }
    }
}
