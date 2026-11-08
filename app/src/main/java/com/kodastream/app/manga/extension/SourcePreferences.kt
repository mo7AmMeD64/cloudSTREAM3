package com.kodastream.app.manga.extension

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore

/**
 * Per-source preferences, stored under the same name Mihon uses (`source_<id>`), so
 * extensions that call `ConfigurableSource.getSourcePreferences()` and this screen see
 * the same values. Uninstalling an extension keeps them unless the user clears data.
 */
internal fun mangaSourcePreferences(context: Context, sourceId: Long): SharedPreferences =
    context.getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)

internal fun clearMangaSourcePreferences(context: Context, sourceId: Long) {
    context.deleteSharedPreferences("source_$sourceId")
}

/** Lets the preference library write straight into the source's SharedPreferences. */
internal class SharedPreferencesDataStore(
    private val prefs: SharedPreferences,
) : PreferenceDataStore() {
    override fun getBoolean(key: String?, defValue: Boolean) = prefs.getBoolean(key, defValue)
    override fun putBoolean(key: String?, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun getInt(key: String?, defValue: Int) = prefs.getInt(key, defValue)
    override fun putInt(key: String?, value: Int) = prefs.edit().putInt(key, value).apply()

    override fun getLong(key: String?, defValue: Long) = prefs.getLong(key, defValue)
    override fun putLong(key: String?, value: Long) = prefs.edit().putLong(key, value).apply()

    override fun getFloat(key: String?, defValue: Float) = prefs.getFloat(key, defValue)
    override fun putFloat(key: String?, value: Float) = prefs.edit().putFloat(key, value).apply()

    override fun getString(key: String?, defValue: String?): String? = prefs.getString(key, defValue)
    override fun putString(key: String?, value: String?) = prefs.edit().putString(key, value).apply()

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        prefs.getStringSet(key, defValues)

    override fun putStringSet(key: String?, values: MutableSet<String>?) =
        prefs.edit().putStringSet(key, values).apply()
}
