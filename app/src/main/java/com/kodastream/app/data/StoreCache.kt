package com.kodastream.app.data

/**
 * In-memory cache for stores that rebuild their list from shared preferences
 * on every read. A write clears the cache so the next read sees the new data.
 */
internal class StoreCache<T> {
    private val values = HashMap<String, T>()

    fun get(key: String, load: () -> T): T = synchronized(values) {
        values[key] ?: load().also { values[key] = it }
    }

    fun clear() = synchronized(values) {
        values.clear()
    }
}
