package com.kodastream.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DataExport
import com.kodastream.app.data.DataExportLogic
import com.kodastream.app.data.DataExportManager
import com.kodastream.app.data.ImportMode
import com.kodastream.app.data.ImportSummary
import com.kodastream.app.data.SearchHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun PrivacySettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pauseHistory by remember { mutableStateOf(AppPrefs.pauseHistory(context)) }
    var pendingImport by remember { mutableStateOf<Pair<DataExport, ImportSummary>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = DataExportManager.exportToUri(context, uri)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(context, R.string.data_export_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.data_export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = DataExportManager.readFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    result.onSuccess { imported ->
                        val current = DataExportManager.createExport(context)
                        val summary = DataExportLogic.computeSummary(current, imported)
                        pendingImport = imported to summary
                    }.onFailure { err ->
                        val msg = context.getString(R.string.data_import_failed, err.message ?: "")
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_privacy_history),
        onBack = onBack,
    ) {
        item(key = "privacy") {
            SettingsGroup(stringResource(R.string.settings_privacy_history)) {
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

        item(key = "data") {
            SettingsGroup(stringResource(R.string.settings_data)) {
                NavigationRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Rounded.Upload,
                    title = stringResource(R.string.settings_export_data),
                    supporting = stringResource(R.string.settings_export_data_summary),
                    onClick = {
                        val defaultName = "cloud3_backup_${LocalDate.now()}.json"
                        exportLauncher.launch(defaultName)
                    },
                )
                NavigationRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.settings_import_data),
                    supporting = stringResource(R.string.settings_import_data_summary),
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    },
                )
            }
        }
    }

    pendingImport?.let { (importedData, summary) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.data_import_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.data_import_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("• " + stringResource(R.string.data_import_section_favorites, summary.favorites.incomingCount, summary.favorites.overwriteCount))
                    Text("• " + stringResource(R.string.data_import_section_watchlist, summary.watchlist.incomingCount, summary.watchlist.overwriteCount))
                    Text("• " + stringResource(R.string.data_import_section_history, summary.history.incomingCount, summary.history.overwriteCount))
                    Text("• " + stringResource(R.string.data_import_section_watched, summary.watched.incomingCount, summary.watched.overwriteCount))
                    Text("• " + stringResource(R.string.data_import_section_search, summary.searchHistory.incomingCount))
                    Text("• " + stringResource(R.string.data_import_section_prefs, summary.appPrefs.incomingCount))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val toImport = importedData
                        pendingImport = null
                        scope.launch(Dispatchers.IO) {
                            DataExportManager.applyImport(context, ImportMode.REPLACE, toImport)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.data_import_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text(
                            text = stringResource(R.string.data_import_dialog_replace),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(onClick = {
                        val toImport = importedData
                        pendingImport = null
                        scope.launch(Dispatchers.IO) {
                            DataExportManager.applyImport(context, ImportMode.MERGE, toImport)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, R.string.data_import_success, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.data_import_dialog_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
