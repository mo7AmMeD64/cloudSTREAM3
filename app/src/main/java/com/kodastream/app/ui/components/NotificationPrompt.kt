package com.kodastream.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.push.Push

/**
 * Asked once, on the first launch after the announcements feature ships.
 * "Not now" keeps the Settings switch available and never asks again.
 */
@Composable
fun NotificationPrompt() {
    val context = LocalContext.current
    var visible by remember {
        mutableStateOf(
            !AppPrefs.notificationsPromptShown(context) &&
                !AppPrefs.notificationsEnabled(context)
        )
    }
    if (!visible) return

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        visible = false
        if (granted) Push.setEnabled(context, true)
    }

    fun enable() {
        AppPrefs.setNotificationsPromptShown(context)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Push.setEnabled(context, true)
            visible = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            AppPrefs.setNotificationsPromptShown(context)
            visible = false
        },
        title = { Text(stringResource(R.string.notifications_prompt_title)) },
        text = { Text(stringResource(R.string.notifications_prompt_message)) },
        confirmButton = {
            TextButton(onClick = { enable() }) {
                Text(stringResource(R.string.notifications_prompt_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AppPrefs.setNotificationsPromptShown(context)
                visible = false
            }) {
                Text(stringResource(R.string.notifications_prompt_later))
            }
        },
    )
}
