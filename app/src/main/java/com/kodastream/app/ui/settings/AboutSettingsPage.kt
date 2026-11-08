package com.kodastream.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.BuildConfig
import com.kodastream.app.R
import com.kodastream.app.crash.CrashRecorder
import com.kodastream.app.ui.crash.CrashReportDialog

private const val GITHUB_URL = "https://github.com/mo7AmMeD64/cloudSTREAM3"
private const val DONATE_URL = "https://creators.sa/x3onq"
private const val TELEGRAM_URL = "https://t.me/fullappk"

@Composable
internal fun AboutSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    fun openLink(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.update_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    var hasCrashReport by remember { mutableStateOf(CrashRecorder.hasReport(context)) }
    var crashText by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
    ) {
        item(key = "about") {
            SettingsGroup(null) {
                val count = if (hasCrashReport) 6 else 5
                InfoRow(
                    index = 0,
                    count = count,
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.app_name),
                    supporting = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                )
                AboutFooterRow(index = 1, count = count)
                NavigationRow(
                    index = 2,
                    count = count,
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.settings_github_title),
                    supporting = stringResource(R.string.settings_github_summary),
                    onClick = { openLink(GITHUB_URL) },
                )
                NavigationRow(
                    index = 3,
                    count = count,
                    icon = Icons.Rounded.VolunteerActivism,
                    title = stringResource(R.string.settings_donate_title),
                    supporting = stringResource(R.string.settings_donate_summary),
                    onClick = { openLink(DONATE_URL) },
                )
                NavigationRow(
                    index = 4,
                    count = count,
                    icon = Icons.AutoMirrored.Rounded.Send,
                    title = stringResource(R.string.settings_telegram_title),
                    supporting = stringResource(R.string.settings_telegram_summary),
                    onClick = { openLink(TELEGRAM_URL) },
                )
                if (hasCrashReport) {
                    NavigationRow(
                        index = 5,
                        count = count,
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.settings_last_crash_title),
                        supporting = stringResource(R.string.settings_last_crash_summary),
                        onClick = {
                            val report = CrashRecorder.readReport(context)
                            if (report != null) crashText = report else hasCrashReport = false
                        },
                    )
                }
            }
        }
    }

    crashText?.let { text ->
        CrashReportDialog(
            text = text,
            onDismiss = { crashText = null },
            allowShare = false,
            allowDelete = true,
            onDeleted = { hasCrashReport = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutFooterRow(index: Int, count: Int) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_nuvio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
