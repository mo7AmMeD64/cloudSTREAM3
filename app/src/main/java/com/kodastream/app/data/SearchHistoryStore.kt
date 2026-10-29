package com.kodastream.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Newest first, case-insensitive de-duplication, short queries never saved. */
fun addSearchHistory(current: List<String>, query: String, max: Int = 20): List<String> {
    val text = query.trim()
    if (text.length < 2) return current
    return (listOf(text) + current.filterNot { it.equals(text, ignoreCase = true) }).take(max)
}

/**
 * Recent search queries, newest first, stored as one JSON array in its own
 * preferences file. Writes happen on submit, delete and clear only.
 */
object SearchHistoryStore {
    private const val PREFS = "kodastream_search"
    private const val KEY = "recent"
    private const val MAX = 20

    private val _entries = MutableStateFlow<List<String>?>(null)

    /** Null until [current] loaded the stored list. */
    val entries: StateFlow<List<String>?> = _entries.asStateFlow()

    fun current(context: Context): List<String> {
        _entries.value?.let { return it }
        val json = prefs(context).getString(KEY, null)
        val stored = if (json.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching {
                val array = JSONArray(json)
                buildList {
                    for (i in 0 until array.length()) {
                        array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())
        }
        _entries.value = stored
        return stored
    }

    fun add(context: Context, query: String) {
        val next = addSearchHistory(current(context), query, MAX)
        if (next != _entries.value) publish(context, next)
    }

    fun remove(context: Context, query: String) {
        publish(context, current(context).filterNot { it == query })
    }

    fun clear(context: Context) {
        publish(context, emptyList())
    }

    private fun publish(context: Context, entries: List<String>) {
        _entries.value = entries
        prefs(context).edit { putString(KEY, JSONArray(entries).toString()) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
