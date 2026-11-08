package com.kodastream.app.ui.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.SearchHistoryStore

@Composable
internal fun PrivacySettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var pauseHistory by remember { mutableStateOf(AppPrefs.pauseHistory(context)) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_privacy_history),
        onBack = onBack,
    ) {
        item(key = "privacy") {
            SettingsGroup(null) {
                SwitchRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Rounded.History,
                    title = stringResource(R.string.settings_pause_history),
                    supporting = stringResource(R.string.settings_pause_history_summary),
                    checked = pauseHistory,
                    onCheckedChange = { value ->
                        AppPrefs.setPauseHistory(context, value)
                        pauseHistory = value
                    },
                )
                NavigationRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.settings_clear_search_history),
                    supporting = stringResource(R.string.settings_clear_search_history_summary),
                    onClick = {
                        SearchHistoryStore.clear(context)
                        Toast.makeText(
                            context,
                            R.string.search_history_cleared,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}
