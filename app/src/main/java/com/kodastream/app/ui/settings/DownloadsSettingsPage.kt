package com.kodastream.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import kotlinx.coroutines.launch

@Composable
internal fun DownloadsSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var downloadWifiOnly by remember { mutableStateOf(AppPrefs.downloadWifiOnly(context)) }
    var downloadConcurrent by remember { mutableStateOf(AppPrefs.downloadMaxConcurrent(context)) }
    var downloadLocation by remember { mutableStateOf(DownloadLocations.readableLocation(context)) }
    var customFolder by remember { mutableStateOf(AppPrefs.downloadTreeUri(context) != null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (DownloadLocations.adoptTree(context, uri)) {
                downloadLocation = DownloadLocations.readableLocation(context)
                customFolder = true
            } else {
                Toast.makeText(context, R.string.download_folder_invalid, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_downloads),
        onBack = onBack,
    ) {
        item(key = "downloads") {
            val count = 3 + if (customFolder) 1 else 0
            SettingsGroup(null) {
                NavigationRow(
                    index = 0,
                    count = count,
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.download_location),
                    supporting = downloadLocation,
                    onClick = { pickFolder.launch(null) },
                )
                if (customFolder) {
                    NavigationRow(
                        index = 1,
                        count = count,
                        icon = Icons.Rounded.Restore,
                        title = stringResource(R.string.download_location_reset),
                        supporting = stringResource(R.string.download_location_reset_summary),
                        onClick = {
                            AppPrefs.setDownloadTreeUri(context, null)
                            downloadLocation = DownloadLocations.readableLocation(context)
                            customFolder = false
                        },
                    )
                }
                SwitchRow(
                    index = if (customFolder) 2 else 1,
                    count = count,
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.download_wifi_only),
                    supporting = stringResource(R.string.download_wifi_summary),
                    checked = downloadWifiOnly,
                    onCheckedChange = { value ->
                        AppPrefs.setDownloadWifiOnly(context, value)
                        downloadWifiOnly = value
                    },
                )
                ChoiceRow(
                    index = if (customFolder) 3 else 2,
                    count = count,
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.download_max_concurrent),
                    options = listOf(1, 2, 3),
                    selected = downloadConcurrent,
                    label = { it.toString() },
                    onSelect = { value ->
                        AppPrefs.setDownloadMaxConcurrent(context, value)
                        downloadConcurrent = value
                    },
                )
            }
        }
    }
}
