package com.kodastream.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val MAX_CODE_ATTEMPTS = 3
private const val RESEND_COOLDOWN_MS = 60_000L

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
    var createAccount by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(SupabaseAuth.lastSync(context)) }
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var codeAttempts by remember { mutableStateOf(0) }
    var resendAllowedAt by remember { mutableStateOf(0L) }

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

    suspend fun afterSignIn(guestFavorites: List<LibraryStore.LibraryItem>) {
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
                    pendingEmail = null
                    code = ""
                    afterSignIn(guestFavorites)
                }
                AuthResult.ConfirmationSent -> {
                    pendingEmail = mail
                    code = ""
                    codeAttempts = 0
                    resendAllowedAt = System.currentTimeMillis() + RESEND_COOLDOWN_MS
                    notice = R.string.account_code_sent
                }
                is AuthResult.Failed -> {
                    if (result.error == AuthError.EMAIL_NOT_CONFIRMED) {
                        pendingEmail = mail
                        code = ""
                        codeAttempts = 0
                        resendAllowedAt = System.currentTimeMillis() + RESEND_COOLDOWN_MS
                        if (SupabaseAuth.resendSignup(mail)) {
                            notice = R.string.account_code_resent
                        } else {
                            error = R.string.account_err_rate
                        }
                    } else {
                        error = authError(result.error)
                    }
                }
            }
            busy = false
        }
    }

    fun verifyCode() {
        if (busy) return
        val mail = pendingEmail ?: return
        if (code.length < 6 || codeAttempts >= MAX_CODE_ATTEMPTS) return
        busy = true
        notice = null
        error = null
        // Still the guest folder: the session only exists after verification.
        val guestFavorites = LibraryStore.favorites(context)
        scope.launch {
            when (val result = SupabaseAuth.verifySignup(context, mail, code.trim())) {
                AuthResult.SignedIn -> {
                    pendingEmail = null
                    code = ""
                    codeAttempts = 0
                    afterSignIn(guestFavorites)
                }
                is AuthResult.Failed -> {
                    if (result.error == AuthError.INVALID_CODE) {
                        codeAttempts++
                        error = if (codeAttempts >= MAX_CODE_ATTEMPTS) {
                            R.string.account_err_attempts
                        } else {
                            R.string.account_err_code
                        }
                    } else {
                        error = authError(result.error)
                    }
                }
                AuthResult.ConfirmationSent -> error = R.string.account_err_unknown
            }
            busy = false
        }
    }

    fun resendCode() {
        if (busy) return
        val mail = pendingEmail ?: return
        if (System.currentTimeMillis() < resendAllowedAt) {
            error = R.string.account_err_wait
            return
        }
        busy = true
        notice = null
        error = null
        scope.launch {
            if (SupabaseAuth.resendSignup(mail)) {
                code = ""
                codeAttempts = 0
                resendAllowedAt = System.currentTimeMillis() + RESEND_COOLDOWN_MS
                notice = R.string.account_code_resent
            } else {
                error = R.string.account_err_rate
            }
            busy = false
        }
    }

    fun cancelCode() {
        if (busy) return
        pendingEmail = null
        code = ""
        codeAttempts = 0
        error = null
        notice = null
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
                        AuthField(
                            value = name,
                            onValueChange = {
                                if (it.length <= 40) {
                                    name = it
                                    AppPrefs.setProfileName(context, it)
                                }
                            },
                            label = stringResource(R.string.account_name),
                            supporting = stringResource(R.string.account_name_hint),
                            modifier = Modifier.weight(1f),
                        )
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
                    } else if (account == null && pendingEmail != null) {
                        Text(
                            text = stringResource(
                                R.string.account_code_hint,
                                pendingEmail.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AuthField(
                            value = code,
                            onValueChange = { value ->
                                if (value.length <= 8 && value.all { it.isDigit() }) code = value
                            },
                            label = stringResource(R.string.account_code),
                            enabled = !busy && codeAttempts < MAX_CODE_ATTEMPTS,
                            keyboardType = KeyboardType.Number,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp,
                            ),
                        )
                        Button(
                            onClick = { verifyCode() },
                            enabled = !busy && code.length >= 6 &&
                                codeAttempts < MAX_CODE_ATTEMPTS,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(stringResource(R.string.account_verify))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TextButton(onClick = { resendCode() }, enabled = !busy) {
                                Text(stringResource(R.string.account_resend))
                            }
                            TextButton(onClick = { cancelCode() }, enabled = !busy) {
                                Text(stringResource(R.string.account_change_email))
                            }
                        }
                    } else if (account == null) {
                        Text(
                            text = stringResource(R.string.account_cloud_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val modes = listOf(
                            R.string.account_sign_in to false,
                            R.string.account_sign_up to true,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { index, (label, isCreate) ->
                                SegmentedButton(
                                    selected = createAccount == isCreate,
                                    onClick = {
                                        createAccount = isCreate
                                        notice = null
                                        error = null
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                ) {
                                    Text(stringResource(label))
                                }
                            }
                        }
                        AuthField(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(R.string.account_email),
                            enabled = !busy,
                            keyboardType = KeyboardType.Email,
                        )
                        AuthField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.account_password),
                            enabled = !busy,
                            keyboardType = KeyboardType.Password,
                            password = true,
                            supporting = if (createAccount) {
                                stringResource(R.string.account_password_hint)
                            } else {
                                null
                            },
                        )
                        Button(
                            onClick = { submit(create = createAccount) },
                            enabled = !busy && email.trim().contains('@') &&
                                (if (createAccount) {
                                    password.length >= 10
                                } else {
                                    password.isNotEmpty()
                                }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (createAccount) {
                                        R.string.account_sign_up
                                    } else {
                                        R.string.account_sign_in
                                    },
                                ),
                            )
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
                    notice?.let { StatusBanner(stringResource(it), error = false) }
                    error?.let { StatusBanner(stringResource(it), error = true) }
                }
            }
        }

        if (busy) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
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
            modifier = Modifier
                .padding(16.dp)
                // Switching between sign in, sign up and the code step changes the
                // card's height; letting it flow keeps the shift from jumping.
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Filled field whose corner relaxes open on focus, in the expressive form language. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    supporting: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var revealed by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val corner by animateDpAsState(
        targetValue = if (focused) 20.dp else 12.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "accountFieldCorner",
    )
    val supportingContent: (@Composable () -> Unit)? = if (supporting == null) {
        null
    } else {
        { Text(supporting) }
    }
    val trailing: (@Composable () -> Unit)? = if (password) {
        {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = stringResource(
                        if (revealed) {
                            R.string.account_hide_password
                        } else {
                            R.string.account_show_password
                        },
                    ),
                )
            }
        }
    } else {
        null
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingContent,
        trailingIcon = trailing,
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (password && !revealed) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        textStyle = textStyle,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Inline result message: tonal for success, error container for failures. */
@Composable
private fun StatusBanner(text: String, error: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (error) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Avatar(path: String?, onClick: () -> Unit) {
    // The cookie shape is the expressive avatar in the reference; the camera badge
    // is the single affordance that the photo can be replaced.
    val shape = MaterialShapes.Cookie12Sided.toShape()
    val description = stringResource(R.string.account_change_photo)
    Box(Modifier.size(88.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (path != null && File(path).exists()) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
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
    AuthError.EMAIL_TAKEN -> R.string.account_err_exists
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.account_err_unconfirmed
    AuthError.WEAK_PASSWORD -> R.string.account_err_weak
    AuthError.RATE_LIMITED -> R.string.account_err_rate
    AuthError.SIGNUP_DISABLED -> R.string.account_err_signup
    AuthError.NOT_CONFIGURED -> R.string.account_disabled
    AuthError.INVALID_CODE -> R.string.account_err_code
    AuthError.UNKNOWN -> R.string.account_err_unknown
}

private fun cloudError(error: CloudError): Int = when (error) {
    CloudError.NOT_CONFIGURED -> R.string.account_disabled
    CloudError.NOT_SIGNED_IN, CloudError.AUTH -> R.string.account_err_auth
    CloudError.NETWORK -> R.string.account_err_network
    CloudError.SERVER, CloudError.FORMAT -> R.string.account_err_server
}
