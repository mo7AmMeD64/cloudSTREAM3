package com.kodastream.app.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kodastream.app.BuildConfig
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.data.SearchHistoryStore
import com.kodastream.app.ui.home.hero.HeroStyle
import com.kodastream.app.ui.theme.APP_PALETTES
import com.kodastream.app.ui.theme.DYNAMIC_PALETTE_ID
import com.kodastream.app.ui.theme.PALETTE_CATEGORIES
import com.kodastream.app.ui.theme.PaletteStyle
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel
import com.kodastream.app.ui.theme.contrastInk

private const val GITHUB_URL = "https://github.com/mo7AmMeD64/cloudSTREAM3"
private const val DONATE_URL = "https://creators.sa/x3onq"
private const val TELEGRAM_URL = "https://t.me/fullappk"

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
    var holdToSpeedUp by remember { mutableStateOf(AppPrefs.holdToSpeedUp(context)) }
    var downloadWifiOnly by remember { mutableStateOf(AppPrefs.downloadWifiOnly(context)) }
    var downloadConcurrent by remember { mutableStateOf(AppPrefs.downloadMaxConcurrent(context)) }
    var downloadLocation by remember { mutableStateOf(DownloadLocations.readableLocation(context)) }
    var customFolder by remember { mutableStateOf(AppPrefs.downloadTreeUri(context) != null) }
    var appLanguage by remember { mutableStateOf(AppPrefs.appLanguage(context)) }
    var languageDialog by remember { mutableStateOf(false) }
    var styleDialog by remember { mutableStateOf(false) }
    var heroStyle by remember { mutableStateOf(HeroStyle.parse(AppPrefs.homeHeroStyle(context))) }
    var heroDialog by remember { mutableStateOf(false) }

    val resolvedDark = when (themeViewModel.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaper = dynamicAvailable && themeViewModel.palette == DYNAMIC_PALETTE_ID

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

    CenteredListBox { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "appearance") {
                val count = 4 + if (resolvedDark) 1 else 0
                SettingsGroup(stringResource(R.string.settings_appearance)) {
                    ChoiceRow(
                        index = 0,
                        count = count,
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.settings_theme),
                        options = ThemeMode.entries,
                        selected = themeViewModel.mode,
                        label = { mode ->
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                }
                            )
                        },
                        onSelect = themeViewModel::updateMode,
                    )
                    NavigationRow(
                        index = 1,
                        count = count,
                        icon = Icons.Rounded.Translate,
                        title = stringResource(R.string.setup_language_title),
                        supporting = languageLabel(appLanguage),
                        onClick = { languageDialog = true },
                    )
                    if (resolvedDark) {
                        SwitchRow(
                            index = 2,
                            count = count,
                            icon = Icons.Rounded.Contrast,
                            title = stringResource(R.string.settings_amoled),
                            supporting = stringResource(R.string.settings_amoled_summary),
                            checked = themeViewModel.amoled,
                            onCheckedChange = themeViewModel::updateAmoled,
                        )
                    }
                    ChoiceRow(
                        index = if (resolvedDark) 3 else 2,
                        count = count,
                        icon = Icons.Rounded.ViewSidebar,
                        title = stringResource(R.string.settings_navigation),
                        options = listOf(AppPrefs.NAV_SIDE, AppPrefs.NAV_BOTTOM),
                        selected = navPosition,
                        label = { position ->
                            stringResource(
                                if (position == AppPrefs.NAV_SIDE) {
                                    R.string.nav_position_side
                                } else {
                                    R.string.nav_position_bottom
                                }
                            )
                        },
                        onSelect = { position ->
                            AppPrefs.setNavPosition(context, position)
                            navPosition = position
                        },
                    )
                    NavigationRow(
                        index = if (resolvedDark) 4 else 3,
                        count = count,
                        icon = Icons.Rounded.ViewCarousel,
                        title = stringResource(R.string.settings_home_hero),
                        supporting = stringResource(heroStyle.labelRes),
                        onClick = { heroDialog = true },
                    )
                }
            }

            item(key = "colors") {
                val firstCategory = if (dynamicAvailable) 1 else 0
                val count = firstCategory + PALETTE_CATEGORIES.size + 1
                SettingsGroup(stringResource(R.string.settings_colors)) {
                    if (dynamicAvailable) {
                        SwitchRow(
                            index = 0,
                            count = count,
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.settings_wallpaper_colors),
                            supporting = stringResource(R.string.settings_wallpaper_colors_summary),
                            checked = wallpaper,
                            onCheckedChange = themeViewModel::setWallpaperColors,
                        )
                    }
                    PALETTE_CATEGORIES.forEachIndexed { categoryIndex, category ->
                        PaletteCategoryRow(
                            index = firstCategory + categoryIndex,
                            count = count,
                            category = category,
                            selectedId = themeViewModel.palette,
                            dimmed = wallpaper,
                            onPick = themeViewModel::updatePalette,
                        )
                    }
                    NavigationRow(
                        index = count - 1,
                        count = count,
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.settings_palette_style),
                        supporting = stringResource(themeViewModel.style.labelRes),
                        onClick = { styleDialog = true },
                    )
                }
            }

            item(key = "playback") {
                SettingsGroup(stringResource(R.string.settings_playback)) {
                    SwitchRow(
                        index = 0,
                        count = 2,
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_keep_screen_on),
                        supporting = stringResource(R.string.settings_keep_screen_on_summary),
                        checked = keepScreenOn,
                        onCheckedChange = { value ->
                            AppPrefs.setKeepScreenOn(context, value)
                            keepScreenOn = value
                        },
                    )
                    SwitchRow(
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.settings_hold_speedup),
                        supporting = stringResource(R.string.settings_hold_speedup_summary),
                        checked = holdToSpeedUp,
                        onCheckedChange = { value ->
                            AppPrefs.setHoldToSpeedUp(context, value)
                            holdToSpeedUp = value
                        },
                    )
                }
            }

            item(key = "downloads") {
                val count = 3 + if (customFolder) 1 else 0
                SettingsGroup(stringResource(R.string.settings_downloads)) {
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

            item(key = "extensions") {
                SettingsGroup(stringResource(R.string.nav_extensions)) {
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

            item(key = "about") {
                SettingsGroup(stringResource(R.string.settings_about)) {
                    val count = 5
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
                        onClick = { uriHandler.openUri(GITHUB_URL) },
                    )
                    NavigationRow(
                        index = 3,
                        count = count,
                        icon = Icons.Rounded.VolunteerActivism,
                        title = stringResource(R.string.settings_donate_title),
                        supporting = stringResource(R.string.settings_donate_summary),
                        onClick = { uriHandler.openUri(DONATE_URL) },
                    )
                    NavigationRow(
                        index = 4,
                        count = count,
                        icon = Icons.Rounded.Send,
                        title = stringResource(R.string.settings_telegram_title),
                        supporting = stringResource(R.string.settings_telegram_summary),
                        onClick = { uriHandler.openUri(TELEGRAM_URL) },
                    )
                }
            }
        }
    }

    if (languageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.setup_language_title),
            options = listOf(AppPrefs.LANGUAGE_SYSTEM, "en", "ar"),
            selected = appLanguage,
            icon = Icons.Rounded.Translate,
            label = { tag -> languageLabel(tag) },
            onSelect = { tag ->
                AppPrefs.setAppLanguage(context, tag)
                appLanguage = tag
                languageDialog = false
                AppPrefs.applyAppLanguage(context)
            },
            onDismiss = { languageDialog = false },
        )
    }

    if (styleDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_palette_style),
            options = PaletteStyle.entries,
            selected = themeViewModel.style,
            icon = Icons.Rounded.Tune,
            label = { style -> stringResource(style.labelRes) },
            onSelect = { style ->
                themeViewModel.updateStyle(style)
                styleDialog = false
            },
            onDismiss = { styleDialog = false },
        )
    }

    if (heroDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_home_hero),
            options = HeroStyle.entries,
            selected = heroStyle,
            icon = Icons.Rounded.ViewCarousel,
            label = { style -> stringResource(style.labelRes) },
            description = { style -> stringResource(style.descriptionRes) },
            onSelect = { style ->
                AppPrefs.setHomeHeroStyle(context, style.storageId)
                heroStyle = style
                heroDialog = false
            },
            onDismiss = { heroDialog = false },
        )
    }
}

@Composable
private fun languageLabel(tag: String): String = stringResource(
    when (tag) {
        "en" -> R.string.setup_language_english
        "ar" -> R.string.setup_language_arabic
        else -> R.string.setup_language_system
    }
)

/**
 * One palette category inside the Colors card. The swatches stay tappable
 * while wallpaper colors are on; a tap turns the switch off and takes that
 * seed, which is the shortest way out of the disabled state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaletteCategoryRow(
    index: Int,
    count: Int,
    category: String,
    selectedId: String,
    dimmed: Boolean,
    onPick: (String) -> Unit,
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .graphicsLayer { alpha = if (dimmed) 0.45f else 1f },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(APP_PALETTES.filter { it.category == category }, key = { it.id }) { palette ->
                    Swatch(
                        first = palette.seedPrimary,
                        second = palette.seedTertiary,
                        selected = palette.id == selectedId,
                        label = palette.name,
                        onClick = { onPick(palette.id) },
                    )
                }
            }
        }
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
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contrastInk(first),
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
