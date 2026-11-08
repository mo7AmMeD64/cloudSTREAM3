package com.kodastream.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs

@Composable
internal fun ExtensionsSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var autoUpdate by remember { mutableStateOf(AppPrefs.autoUpdate(context)) }

    SettingsPageScaffold(
        title = stringResource(R.string.nav_extensions),
        onBack = onBack,
    ) {
        item(key = "extensions") {
            SettingsGroup(null) {
                SwitchRow(
                    index = 0,
                    count = 1,
                    icon = Icons.Rounded.Extension,
                    title = stringResource(R.string.settings_auto_update),
                    supporting = stringResource(R.string.settings_auto_update_summary),
                    checked = autoUpdate,
                    onCheckedChange = { value ->
                        AppPrefs.setAutoUpdate(context, value)
                        autoUpdate = value
                    },
                )
            }
        }
    }
}
