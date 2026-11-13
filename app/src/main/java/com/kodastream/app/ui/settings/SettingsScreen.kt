package com.kodastream.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kodastream.app.BuildConfig
import com.kodastream.app.R
import com.kodastream.app.ui.theme.ThemeViewModel

/**
 * Settings root: a short list of category "folders". Every category opens its
 * own page that holds only its settings; the pages live next to this file.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenCategory: (SettingsCategory) -> Unit,
) {
    CenteredListBox { gutter ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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

            SettingsCategory.clusters.forEach { cluster ->
                item(key = "cluster_${cluster.first().id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        cluster.forEachIndexed { index, category ->
                            NavigationRow(
                                index = index,
                                count = cluster.size,
                                icon = categoryIcon(category),
                                title = stringResource(categoryTitle(category)),
                                supporting = stringResource(categorySummary(category)),
                                onClick = { onOpenCategory(category) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One category page; the host route passes the callbacks the manga rows need. */
@Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onOpenColors: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onOpenMangaExtensions: () -> Unit,
    onOpenMangaStores: () -> Unit,
    onOpenMangaDownloads: () -> Unit,
    onMangaSectionChanged: (Boolean) -> Unit,
) {
    when (category) {
        SettingsCategory.APPEARANCE -> AppearanceSettingsPage(
            themeViewModel = themeViewModel,
            onOpenColors = onOpenColors,
            onOpenAppIcon = onOpenAppIcon,
            onBack = onBack,
        )
        SettingsCategory.PLAYER -> PlayerSettingsPage(onBack = onBack)
        SettingsCategory.DOWNLOADS -> DownloadsSettingsPage(onBack = onBack)
        SettingsCategory.PRIVACY -> PrivacySettingsPage(onBack = onBack)
        SettingsCategory.STATISTICS -> StatisticsSettingsPage(onBack = onBack)
        SettingsCategory.EXTENSIONS -> ExtensionsSettingsPage(onBack = onBack)
        SettingsCategory.MANGA -> MangaSettingsPage(
            onBack = onBack,
            onOpenMangaExtensions = onOpenMangaExtensions,
            onOpenMangaStores = onOpenMangaStores,
            onOpenMangaDownloads = onOpenMangaDownloads,
            onMangaSectionChanged = onMangaSectionChanged,
        )
        SettingsCategory.ABOUT -> AboutSettingsPage(onBack = onBack)
    }
}

@StringRes
private fun categoryTitle(category: SettingsCategory): Int = when (category) {
    SettingsCategory.APPEARANCE -> R.string.settings_appearance
    SettingsCategory.PLAYER -> R.string.settings_cat_player
    SettingsCategory.DOWNLOADS -> R.string.settings_downloads
    SettingsCategory.PRIVACY -> R.string.settings_privacy_history
    SettingsCategory.STATISTICS -> R.string.settings_cat_statistics
    SettingsCategory.EXTENSIONS -> R.string.nav_extensions
    SettingsCategory.MANGA -> R.string.settings_manga
    SettingsCategory.ABOUT -> R.string.settings_about
}

@StringRes
private fun categorySummary(category: SettingsCategory): Int = when (category) {
    SettingsCategory.APPEARANCE -> R.string.settings_cat_appearance_summary
    SettingsCategory.PLAYER -> R.string.settings_cat_player_summary
    SettingsCategory.DOWNLOADS -> R.string.settings_cat_downloads_summary
    SettingsCategory.PRIVACY -> R.string.settings_cat_privacy_summary
    SettingsCategory.STATISTICS -> R.string.settings_cat_statistics_summary
    SettingsCategory.EXTENSIONS -> R.string.settings_cat_extensions_summary
    SettingsCategory.MANGA -> R.string.settings_cat_manga_summary
    SettingsCategory.ABOUT -> R.string.settings_cat_about_summary
}

private fun categoryIcon(category: SettingsCategory): ImageVector = when (category) {
    SettingsCategory.APPEARANCE -> Icons.Rounded.Palette
    SettingsCategory.PLAYER -> Icons.Rounded.PlayCircle
    SettingsCategory.DOWNLOADS -> Icons.Rounded.Download
    SettingsCategory.PRIVACY -> Icons.Rounded.History
    SettingsCategory.STATISTICS -> Icons.Rounded.BarChart
    SettingsCategory.EXTENSIONS -> Icons.Rounded.Extension
    SettingsCategory.MANGA -> Icons.Rounded.AutoStories
    SettingsCategory.ABOUT -> Icons.Rounded.Info
}
