package com.kodastream.app.data

import android.content.Context
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import org.json.JSONArray
import org.json.JSONObject

/** The row the account keeps on the server; null means no backup yet. */
data class RemoteBackup(
    val favorites: List<LibraryStore.LibraryItem>,
    val updatedAt: String?,
)

enum class CloudError {
    NOT_CONFIGURED,
    NOT_SIGNED_IN,
    NETWORK,
    AUTH,
    SERVER,
    FORMAT,
}

sealed interface UploadResult {
    data object Success : UploadResult
    data class Failed(val error: CloudError) : UploadResult
}

sealed interface DownloadResult {
    data class Success(val backup: RemoteBackup?) : DownloadResult
    data class Failed(val error: CloudError) : DownloadResult
}

/**
 * Favorites-only cloud backup. A full snapshot replaces the row on upload, so
 * the last device to change something wins; restoring is always explicit.
 */
object FavoritesBackup {
    internal fun encodeFavorites(items: List<LibraryStore.LibraryItem>): String =
        mapper.writeValueAsString(items)

    internal fun decodeFavorites(raw: String): List<LibraryStore.LibraryItem> =
        mapper.readValue(raw)

    suspend fun upload(context: Context): UploadResult {
        if (!SupabaseHttp.configured) return UploadResult.Failed(CloudError.NOT_CONFIGURED)
        val uid = SupabaseAuth.userId(context)
            ?: return UploadResult.Failed(CloudError.NOT_SIGNED_IN)
        val token = SupabaseAuth.accessToken(context)
            ?: return UploadResult.Failed(CloudError.AUTH)
        val body = JSONObject()
            .put("user_id", uid)
            .put("favorites", JSONArray(encodeFavorites(LibraryStore.favorites(context))))
        val reply = SupabaseHttp.send(
            method = "POST",
            path = "/rest/v1/favorites_backup",
            body = body,
            bearer = token,
            query = "on_conflict=user_id",
            headers = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        return when {
            reply.ok -> {
                SupabaseAuth.setLastSync(context, System.currentTimeMillis())
                SupabaseAuth.setCloudReady(context, true)
                UploadResult.Success
            }
            reply.code == 0 -> UploadResult.Failed(CloudError.NETWORK)
            reply.code == 401 || reply.code == 403 -> UploadResult.Failed(CloudError.AUTH)
            else -> UploadResult.Failed(CloudError.SERVER)
        }
    }

    suspend fun download(context: Context): DownloadResult {
        if (!SupabaseHttp.configured) return DownloadResult.Failed(CloudError.NOT_CONFIGURED)
        val uid = SupabaseAuth.userId(context)
            ?: return DownloadResult.Failed(CloudError.NOT_SIGNED_IN)
        val token = SupabaseAuth.accessToken(context)
            ?: return DownloadResult.Failed(CloudError.AUTH)
        val reply = SupabaseHttp.send(
            method = "GET",
            path = "/rest/v1/favorites_backup",
            bearer = token,
            query = "select=favorites,updated_at&user_id=eq.$uid",
        )
        if (!reply.ok) {
            return DownloadResult.Failed(
                when (reply.code) {
                    0 -> CloudError.NETWORK
                    401, 403 -> CloudError.AUTH
                    else -> CloudError.SERVER
                },
            )
        }
        val rows = runCatching { JSONArray(reply.raw) }
            .getOrElse { return DownloadResult.Failed(CloudError.FORMAT) }
        if (rows.length() == 0) return DownloadResult.Success(null)
        val row = rows.optJSONObject(0) ?: return DownloadResult.Success(null)
        val favorites = runCatching {
            decodeFavorites(row.optJSONArray("favorites")?.toString() ?: "[]")
        }.getOrElse { return DownloadResult.Failed(CloudError.FORMAT) }
        return DownloadResult.Success(
            RemoteBackup(
                favorites = favorites,
                updatedAt = row.optString("updated_at").takeIf { it.isNotBlank() },
            ),
        )
    }
}
