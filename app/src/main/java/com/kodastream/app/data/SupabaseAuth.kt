package com.kodastream.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class Account(val userId: String, val email: String)

enum class AuthError {
    NETWORK,
    INVALID_CREDENTIALS,
    EMAIL_TAKEN,
    EMAIL_NOT_CONFIRMED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    SIGNUP_DISABLED,
    NOT_CONFIGURED,
    INVALID_CODE,
    UNKNOWN,
}

sealed interface AuthResult {
    data object SignedIn : AuthResult

    /** Sign-up succeeded; the address still has to be confirmed by email. */
    data object ConfirmationSent : AuthResult

    data class Failed(val error: AuthError) : AuthResult
}

/**
 * Email + password accounts. Only the refresh token and the email are kept on
 * disk, both encrypted with the Android Keystore key; the access token lives
 * in memory and is refreshed on demand. The password is never stored.
 */
object SupabaseAuth {
    private const val PREFS = "kodastream_account"
    private const val KEY_UID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_CLOUD_READY = "cloud_ready"

    @Volatile private var cachedAccess: String? = null
    @Volatile private var cachedExpiry: Long = 0L
    private val refreshMutex = Mutex()

    val enabled: Boolean get() = SupabaseHttp.configured

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun userId(context: Context): String? =
        prefs(context).getString(KEY_UID, null)

    fun account(context: Context): Account? {
        val p = prefs(context)
        val uid = p.getString(KEY_UID, null) ?: return null
        val email = p.getString(KEY_EMAIL, null)?.let(SecretCipher::decrypt) ?: return null
        return Account(uid, email)
    }

    fun isSignedIn(context: Context): Boolean =
        userId(context) != null && prefs(context).getString(KEY_REFRESH, null) != null

    fun lastSync(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSync(context: Context, value: Long) {
        prefs(context).edit { putLong(KEY_LAST_SYNC, value) }
    }

    /**
     * True once this session has seen the cloud copy. Auto-upload stays off
     * before that, so a fresh install can never replace a backup with an
     * empty local list before the download has run.
     */
    fun isCloudReady(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOUD_READY, false)

    fun setCloudReady(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLOUD_READY, value) }
    }

    suspend fun signUp(context: Context, email: String, password: String): AuthResult {
        if (!enabled) return AuthResult.Failed(AuthError.NOT_CONFIGURED)
        val reply = SupabaseHttp.send(
            "POST",
            "/auth/v1/signup",
            body = JSONObject().put("email", email).put("password", password),
        )
        if (!reply.ok) return AuthResult.Failed(errorOf(reply))
        // Supabase hides existing accounts behind a fake user with no identity.
        if (reply.json.optJSONArray("identities")?.length() == 0) {
            return AuthResult.Failed(AuthError.EMAIL_TAKEN)
        }
        return if (reply.json.optString("access_token").isBlank()) {
            AuthResult.ConfirmationSent
        } else {
            remember(context, reply.json)?.let { AuthResult.SignedIn }
                ?: AuthResult.Failed(AuthError.UNKNOWN)
        }
    }

    suspend fun signIn(context: Context, email: String, password: String): AuthResult {
        if (!enabled) return AuthResult.Failed(AuthError.NOT_CONFIGURED)
        val reply = SupabaseHttp.send(
            "POST",
            "/auth/v1/token",
            body = JSONObject().put("email", email).put("password", password),
            query = "grant_type=password",
        )
        if (!reply.ok) return AuthResult.Failed(errorOf(reply))
        return remember(context, reply.json)?.let { AuthResult.SignedIn }
            ?: AuthResult.Failed(AuthError.UNKNOWN)
    }

    /** Confirms a new account with the code from the signup email. */
    suspend fun verifySignup(context: Context, email: String, code: String): AuthResult {
        if (!enabled) return AuthResult.Failed(AuthError.NOT_CONFIGURED)
        val reply = SupabaseHttp.send(
            "POST",
            "/auth/v1/verify",
            body = JSONObject()
                .put("type", "signup")
                .put("email", email)
                .put("token", code),
        )
        if (!reply.ok) return AuthResult.Failed(errorOf(reply))
        return remember(context, reply.json)?.let { AuthResult.SignedIn }
            ?: AuthResult.Failed(AuthError.UNKNOWN)
    }

    /** Sends a fresh signup code; false when the request was rejected. */
    suspend fun resendSignup(email: String): Boolean {
        if (!enabled) return false
        return SupabaseHttp.send(
            "POST",
            "/auth/v1/resend",
            body = JSONObject().put("type", "signup").put("email", email),
        ).ok
    }

    suspend fun signOut(context: Context) {
        val token = accessToken(context)
        if (token != null && enabled) {
            SupabaseHttp.send("POST", "/auth/v1/logout", body = JSONObject(), bearer = token)
        }
        clearSession(context)
    }

    /** A valid access token, refreshing first when it is missing or nearly expired. */
    suspend fun accessToken(context: Context): String? {
        val token = cachedAccess
        if (token != null && System.currentTimeMillis() < cachedExpiry - 60_000L) return token
        return refreshMutex.withLock {
            val current = cachedAccess
            if (current != null && System.currentTimeMillis() < cachedExpiry - 60_000L) {
                current
            } else {
                refresh(context)
            }
        }
    }

    fun clearSession(context: Context) {
        cachedAccess = null
        cachedExpiry = 0L
        prefs(context).edit { clear() }
    }

    private suspend fun refresh(context: Context): String? {
        val stored = prefs(context).getString(KEY_REFRESH, null)
            ?.let(SecretCipher::decrypt) ?: return null
        val reply = SupabaseHttp.send(
            "POST",
            "/auth/v1/token",
            body = JSONObject().put("refresh_token", stored),
            query = "grant_type=refresh_token",
        )
        if (!reply.ok) {
            // Only a rejected token means the session is gone; offline and
            // server errors must not delete it.
            if (reply.code == 400 || reply.code == 401 || reply.code == 403) {
                clearSession(context)
            }
            return null
        }
        return remember(context, reply.json)
    }

    /** Persists the rotated tokens; returns the access token or null on a bad reply. */
    private fun remember(context: Context, json: JSONObject): String? {
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val user = json.optJSONObject("user")
        val uid = user?.optString("id")?.takeIf { it.isNotBlank() } ?: userId(context)
        val email = user?.optString("email")?.takeIf { it.isNotBlank() }
            ?: account(context)?.email
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: prefs(context).getString(KEY_REFRESH, null)?.let(SecretCipher::decrypt)
        if (uid == null || email == null || refresh == null) return null

        val encryptedEmail = SecretCipher.encrypt(email) ?: return null
        val encryptedRefresh = SecretCipher.encrypt(refresh) ?: return null
        prefs(context).edit {
            putString(KEY_UID, uid)
            putString(KEY_EMAIL, encryptedEmail)
            putString(KEY_REFRESH, encryptedRefresh)
        }
        cachedAccess = access
        val seconds = json.optLong("expires_in", 3600L).coerceIn(60L, 86_400L)
        cachedExpiry = System.currentTimeMillis() + seconds * 1000L
        return access
    }

    private fun errorOf(reply: SupabaseHttp.Reply): AuthError {
        if (reply.code == 0) return AuthError.NETWORK
        val body = reply.json
        val code = body.optString("error_code").ifBlank { body.optString("error") }
        val message = body.optString("msg").ifBlank { body.optString("error_description") }
        return when {
            code == "email_not_confirmed" ||
                message.contains("not confirmed", ignoreCase = true) ->
                AuthError.EMAIL_NOT_CONFIRMED
            code == "user_already_exists" ||
                message.contains("already registered", ignoreCase = true) ->
                AuthError.EMAIL_TAKEN
            code == "otp_expired" || code == "invalid_otp" ||
                message.contains("Token has expired", ignoreCase = true) ->
                AuthError.INVALID_CODE
            code == "invalid_credentials" || code == "invalid_grant" ||
                message.contains("Invalid login credentials", ignoreCase = true) ->
                AuthError.INVALID_CREDENTIALS
            code == "weak_password" -> AuthError.WEAK_PASSWORD
            code.contains("rate_limit") || reply.code == 429 -> AuthError.RATE_LIMITED
            code == "signup_disabled" -> AuthError.SIGNUP_DISABLED
            else -> AuthError.UNKNOWN
        }
    }
}
