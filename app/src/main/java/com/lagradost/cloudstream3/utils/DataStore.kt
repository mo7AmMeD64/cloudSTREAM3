package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral

/**
 * Key/value storage with the same shape as CloudStream's DataStore.
 *
 * Extensions call `getKey` / `setKey` (usually through CloudStreamApp) to keep
 * cookies, tokens and settings, so the file name, the "folder/path" key format
 * and the JSON encoding are kept identical to the original.
 */
const val PREFERENCES_NAME = "rebuild_preference"

object DataStore {
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences = getPreferences(this)

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
    }

    fun getFolderName(folder: String, path: String): String = "${folder}/${path}"

    fun Context.getKeys(folder: String): List<String> {
        // Ensure that the folder ends with "/" to prevent matching with other folders
        val fixedFolder = folder.trimEnd('/') + "/"
        return this.getSharedPrefs().all.keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        return getSharedPrefs().contains(path)
    }

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                prefs.edit {
                    remove(path)
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        try {
            getSharedPrefs().edit {
                keys.forEach { value ->
                    remove(value)
                }
            }
            return keys.size
        } catch (e: Exception) {
            logError(e)
            return 0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit {
                putString(path, value?.toJsonLiteral())
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            parseJson(json, valueType.kotlin)
        } catch (_: Exception) {
            null
        }
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        return try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            parseJson<T>(json)
        } catch (_: Exception) {
            null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}
