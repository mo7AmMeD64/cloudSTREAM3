package com.kodastream.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.BuildConfig
import com.kodastream.app.R
import androidx.documentfile.provider.DocumentFile
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.ui.theme.APP_PALETTES
import com.kodastream.app.ui.theme.DYNAMIC_PALETTE_ID
import com.kodastream.app.ui.theme.PALETTE_CATEGORIES
import com.kodastream.app.ui.theme.PaletteStyle
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeViewModel: ThemeViewModel,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var autoUpdate by remember { mutableStateOf(AppPrefs.autoUpdate(context)) }
    var navPosition by remember { mutableStateOf(AppPrefs.navPosition(context)) }
    var pauseHistory by remember { mutableStateOf(AppPrefs.pauseHistory(context)) }
    var keepScreenOn by remember { mutableStateOf(AppPrefs.keepScreenOn(context)) }
    var downloadWifiOnly by remember { mutableStateOf(AppPrefs.downloadWifiOnly(context)) }
    var downloadConcurrent by remember { mutableStateOf(AppPrefs.downloadMaxConcurrent(context)) }
    var downloadLocation by remember { mutableStateOf(DownloadLocations.readableLocation(context)) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            if (DocumentFile.fromTreeUri(context, uri)?.canWrite() == true) {
                AppPrefs.setDownloadTreeUri(context, uri.toString())
                downloadLocation = DownloadLocations.readableLocation(context)
            } else {
                Toast.makeText(context, R.string.download_folder_invalid, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ---------------------------------------------------------------- appearance
        item { GroupTitle(stringResource(R.string.settings_theme)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ThemeMode.entries.toList()) { mode ->
                    FilterChip(
                        selected = themeViewModel.mode == mode,
                        onClick = { themeViewModel.updateMode(mode) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- navigation
        item { GroupTitle(stringResource(R.string.settings_navigation)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = navPosition == AppPrefs.NAV_SIDE,
                        onClick = {
                            AppPrefs.setNavPosition(context, AppPrefs.NAV_SIDE)
                            navPosition = AppPrefs.NAV_SIDE
                        },
                        label = { Text(stringResource(R.string.nav_position_side)) },
                    )
                }
                item {
                    FilterChip(
                        selected = navPosition == AppPrefs.NAV_BOTTOM,
                        onClick = {
                            AppPrefs.setNavPosition(context, AppPrefs.NAV_BOTTOM)
                            navPosition = AppPrefs.NAV_BOTTOM
                        },
                        label = { Text(stringResource(R.string.nav_position_bottom)) },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- playback
        item { GroupTitle(stringResource(R.string.settings_playback)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                summary = stringResource(R.string.settings_keep_screen_on_summary),
                checked = keepScreenOn,
                onCheckedChange = { value ->
                    AppPrefs.setKeepScreenOn(context, value)
                    keepScreenOn = value
                },
            )
        }

        // ---------------------------------------------------------------- history
        item {
            SwitchRow(
                title = stringResource(R.string.settings_pause_history),
                summary = stringResource(R.string.settings_pause_history_summary),
                checked = pauseHistory,
                onCheckedChange = { value ->
                    AppPrefs.setPauseHistory(context, value)
                    pauseHistory = value
                },
            )
        }

        // ---------------------------------------------------------------- downloads
        item { GroupTitle(stringResource(R.string.settings_downloads)) }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.download_location),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = downloadLocation,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    TextButton(onClick = { pickFolder.launch(null) }) {
                        Text(stringResource(R.string.download_location_change))
                    }
                    TextButton(
                        onClick = {
                            AppPrefs.setDownloadTreeUri(context, null)
                            downloadLocation = DownloadLocations.readableLocation(context)
                        },
                    ) {
                        Text(stringResource(R.string.download_location_reset))
                    }
                }
            }
        }
        item {
            SwitchRow(
                title = stringResource(R.string.download_wifi_only),
                summary = stringResource(R.string.download_wifi_summary),
                checked = downloadWifiOnly,
                onCheckedChange = { value ->
                    AppPrefs.setDownloadWifiOnly(context, value)
                    downloadWifiOnly = value
                },
            )
        }
        item {
            Text(
                text = stringResource(R.string.download_max_concurrent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf(1, 2, 3)) { count ->
                    FilterChip(
                        selected = downloadConcurrent == count,
                        onClick = {
                            AppPrefs.setDownloadMaxConcurrent(context, count)
                            downloadConcurrent = count
                        },
                        label = { Text(count.toString()) },
                    )
                }
            }
        }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_amoled),
                summary = stringResource(R.string.settings_amoled_summary),
                checked = themeViewModel.amoled,
                onCheckedChange = themeViewModel::updateAmoled,
            )
        }

        item { GroupTitle(stringResource(R.string.settings_colors)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = themeViewModel.palette == DYNAMIC_PALETTE_ID,
                        onClick = { themeViewModel.updatePalette(DYNAMIC_PALETTE_ID) },
                        label = { Text(stringResource(R.string.palette_wallpaper)) },
                    )
                }
            }
        }
        PALETTE_CATEGORIES.forEach { category ->
            item(key = "palette-$category") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(APP_PALETTES.filter { it.category == category }) { palette ->
                            Swatch(
                                first = palette.seedPrimary,
                                second = palette.seedTertiary,
                                selected = themeViewModel.palette == palette.id,
                                label = palette.name,
                                onClick = { themeViewModel.updatePalette(palette.id) },
                            )
                        }
                    }
                }
            }
        }

        item { GroupTitle(stringResource(R.string.settings_palette_style)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PaletteStyle.entries.toList()) { style ->
                    FilterChip(
                        selected = themeViewModel.style == style,
                        onClick = { themeViewModel.updateStyle(style) },
                        label = { Text(stringResource(style.labelRes)) },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- extensions
        item { GroupTitle(stringResource(R.string.nav_extensions)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_auto_update),
                summary = stringResource(R.string.settings_auto_update_summary),
                checked = autoUpdate,
                onCheckedChange = {
                    autoUpdate = it
                    AppPrefs.setAutoUpdate(context, it)
                },
            )
        }

        // ---------------------------------------------------------------- about
        item { GroupTitle(stringResource(R.string.settings_about)) }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name) + "  " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://github.com/mo7AmMeD64/cloudSTREAM3")
                    },
                ) {
                    Text(stringResource(R.string.settings_github))
                }
                TextButton(
                    onClick = { uriHandler.openUri("https://creators.sa/x3onq") },
                ) {
                    Text(stringResource(R.string.settings_donate))
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A round preview of a palette: two of its seed colors side by side. */
@Composable
private fun Swatch(
    first: Color,
    second: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(first, second)))
                .border(
                    border = BorderStroke(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
