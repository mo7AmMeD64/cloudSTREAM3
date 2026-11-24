package com.kodastream.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.AuthError
import com.kodastream.app.data.AuthResult
import com.kodastream.app.data.CloudError
import com.kodastream.app.data.DownloadResult
import com.kodastream.app.data.FavoritesBackup
import com.kodastream.app.data.FavoritesSync
import com.kodastream.app.data.LibraryStore
import com.kodastream.app.data.SupabaseAuth
import com.kodastream.app.data.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Local name and photo plus the email/password account that backs favorites up. */
@Composable
internal fun AccountSettingsPage(onBack: () -> Unit) {
    KeepSecureScreen()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(AppPrefs.profileName(context)) }
    var avatar by remember { mutableStateOf(AppPrefs.profileAvatar(context)) }
    var account by remember { mutableStateOf(SupabaseAuth.account(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var choice by remember { mutableStateOf<AuthChoice?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(SupabaseAuth.lastSync(context)) }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyAvatar(context, uri) }
                if (path != null) {
                    avatar = path
                    AppPrefs.setProfileAvatar(context, path)
                }
            }
        }
    }

    fun submit(create: Boolean) {
        if (busy) return
        val mail = email.trim()
        if (!mail.contains('@')) return
        if (create && password.length < 10) return
        busy = true
        notice = null
        error = null
        // Captured before the session flips the store to the account namespace.
        val guestFavorites = LibraryStore.favorites(context)
        scope.launch {
            val result = if (create) {
                SupabaseAuth.signUp(context, mail, password)
            } else {
                SupabaseAuth.signIn(context, mail, password)
            }
            password = ""
            when (result) {
                AuthResult.SignedIn -> {
                    account = SupabaseAuth.account(context)
                    LibraryStore.notifyChanged()
                    FavoritesSync.schedule(context)
                    when (val download = FavoritesBackup.download(context)) {
                        is DownloadResult.Success -> {
                            val remote = download.backup?.favorites.orEmpty()
                            when {
                                remote.isEmpty() && guestFavorites.isNotEmpty() -> {
                                    LibraryStore.replaceFavorites(context, guestFavorites)
                                    SupabaseAuth.setCloudReady(context, true)
                                    if (FavoritesBackup.upload(context) is UploadResult.Success) {
                                        lastSync = SupabaseAuth.lastSync(context)
                                        notice = R.string.account_sync_ok
                                    }
                                }
                                remote.isNotEmpty() && guestFavorites.isEmpty() -> {
                                    LibraryStore.replaceFavorites(context, remote)
                                    SupabaseAuth.setCloudReady(context, true)
                                    notice = R.string.account_restore_ok
                                }
                                remote.isNotEmpty() && guestFavorites.isNotEmpty() -> {
                                    choice = AuthChoice(guestFavorites, remote)
                                }
                                else -> {
                                    SupabaseAuth.setCloudReady(context, true)
                                }
                            }
                        }
                        is DownloadResult.Failed -> {
                            error = cloudError(download.error)
                        }
                    }
                }
                AuthResult.ConfirmationSent -> {
                    notice = R.string.account_check_email
                }
                is AuthResult.Failed -> {
                    error = authError(result.error)
                }
            }
            busy = false
        }
    }

    fun syncNow() {
        if (busy) return
        busy = true
        notice = null
        error = null
        scope.launch {
            when (val result = FavoritesBackup.upload(context)) {
                UploadResult.Success -> {
                    lastSync = SupabaseAuth.lastSync(context)
                    notice = R.string.account_sync_ok
                }
                is UploadResult.Failed -> error = cloudError(result.error)
            }
            busy = false
        }
    }

    fun restore() {
        if (busy) return
        busy = true
        notice = null
        error = null
        scope.launch {
            when (val result = FavoritesBackup.download(context)) {
                is DownloadResult.Success -> {
                    SupabaseAuth.setCloudReady(context, true)
                    val backup = result.backup
                    if (backup == null) {
                        error = R.string.account_no_backup
                    } else {
                        LibraryStore.replaceFavorites(context, backup.favorites)
                        notice = R.string.account_restore_ok
                    }
                }
                is DownloadResult.Failed -> error = cloudError(result.error)
            }
            busy = false
        }
    }

    fun useCloud(pending: AuthChoice) {
        choice = null
        SupabaseAuth.setCloudReady(context, true)
        LibraryStore.replaceFavorites(context, pending.remote)
        notice = R.string.account_restore_ok
    }

    fun useLocal(pending: AuthChoice) {
        choice = null
        SupabaseAuth.setCloudReady(context, true)
        LibraryStore.replaceFavorites(context, pending.local)
        syncNow()
    }

    fun signOut() {
        if (busy) return
        busy = true
        scope.launch {
            // Best effort: do not lose the last five seconds of edits.
            if (SupabaseAuth.isCloudReady(context)) FavoritesBackup.upload(context)
            SupabaseAuth.signOut(context)
            FavoritesSync.cancel(context)
            account = null
            LibraryStore.notifyChanged()
            notice = null
            error = null
            password = ""
            busy = false
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_account),
        onBack = onBack,
    ) {
        item {
            SettingsGroup(title = stringResource(R.string.account_profile)) {
                AccountCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            path = avatar,
                            onClick = {
                                pickAvatar.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        )
                        Spacer(Modifier.width(16.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                if (it.length <= 40) {
                                    name = it
                                    AppPrefs.setProfileName(context, it)
                                }
                            },
                            label = { Text(stringResource(R.string.account_name)) },
                            supportingText = { Text(stringResource(R.string.account_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            pickAvatar.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.account_change_photo))
                    }
                }
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.account_cloud)) {
                AccountCard {
                    if (!SupabaseAuth.enabled) {
                        Text(
                            text = stringResource(R.string.account_disabled),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (account == null) {
                        Text(
                            text = stringResource(R.string.account_cloud_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.account_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.account_password)) },
                            supportingText = {
                                Text(stringResource(R.string.account_password_hint))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { submit(create = false) },
                                enabled = !busy && email.contains('@') && password.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.account_sign_in))
                            }
                            TextButton(
                                onClick = { submit(create = true) },
                                enabled = !busy && email.contains('@') && password.length >= 10,
                            ) {
                                Text(stringResource(R.string.account_sign_up))
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(
                                R.string.account_signed_in,
                                account?.email.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (lastSync == 0L) {
                                stringResource(R.string.account_never_synced)
                            } else {
                                stringResource(
                                    R.string.account_last_sync,
                                    formatDate(lastSync),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { syncNow() }, enabled = !busy) {
                                Text(stringResource(R.string.account_sync_now))
                            }
                            TextButton(
                                onClick = { confirmRestore = true },
                                enabled = !busy,
                            ) {
                                Text(stringResource(R.string.account_restore))
                            }
                        }
                        TextButton(onClick = { signOut() }, enabled = !busy) {
                            Text(stringResource(R.string.account_sign_out))
                        }
                    }
                }
            }
        }

        if (busy) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        notice?.let { id ->
            item {
                Text(
                    text = stringResource(id),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        error?.let { id ->
            item {
                Text(
                    text = stringResource(id),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.account_restore_title)) },
            text = { Text(stringResource(R.string.account_restore_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        restore()
                    },
                ) {
                    Text(stringResource(R.string.account_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    choice?.let { pending ->
        AlertDialog(
            onDismissRequest = { useCloud(pending) },
            title = { Text(stringResource(R.string.account_choice_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.account_choice_body,
                        pending.local.size,
                        pending.remote.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { useCloud(pending) }) {
                    Text(stringResource(R.string.account_choice_cloud))
                }
            },
            dismissButton = {
                TextButton(onClick = { useLocal(pending) }) {
                    Text(stringResource(R.string.account_choice_local))
                }
            },
        )
    }
}

private data class AuthChoice(
    val local: List<LibraryStore.LibraryItem>,
    val remote: List<LibraryStore.LibraryItem>,
)

@Composable
private fun AccountCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun Avatar(path: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null && File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.account_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = stringResource(R.string.account_photo),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** Keeps the email and password off screenshots and the recent-apps thumbnail. */
@Composable
private fun KeepSecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun copyAvatar(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "profile").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val target = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
    val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
    input.use { source ->
        target.outputStream().use { sink -> source.copyTo(sink) }
    }
    target.absolutePath
}.getOrNull()

private fun formatDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

private fun authError(error: AuthError): Int = when (error) {
    AuthError.NETWORK -> R.string.account_err_network
    AuthError.INVALID_CREDENTIALS -> R.string.account_err_credentials
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.account_err_unconfirmed
    AuthError.WEAK_PASSWORD -> R.string.account_err_weak
    AuthError.RATE_LIMITED -> R.string.account_err_rate
    AuthError.SIGNUP_DISABLED -> R.string.account_err_signup
    AuthError.NOT_CONFIGURED -> R.string.account_disabled
    AuthError.UNKNOWN -> R.string.account_err_unknown
}

private fun cloudError(error: CloudError): Int = when (error) {
    CloudError.NOT_CONFIGURED -> R.string.account_disabled
    CloudError.NOT_SIGNED_IN, CloudError.AUTH -> R.string.account_err_auth
    CloudError.NETWORK -> R.string.account_err_network
    CloudError.SERVER, CloudError.FORMAT -> R.string.account_err_server
}
