package com.kodastream.app.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewSidebar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.ui.home.hero.HeroStyle
import com.kodastream.app.ui.theme.APP_PALETTES
import com.kodastream.app.ui.theme.DYNAMIC_PALETTE_ID
import com.kodastream.app.ui.theme.PALETTE_CATEGORIES
import com.kodastream.app.ui.theme.PaletteStyle
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel
import com.kodastream.app.ui.theme.contrastInk

@Composable
internal fun AppearanceSettingsPage(
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var navPosition by remember { mutableStateOf(AppPrefs.navPosition(context)) }
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

    SettingsPageScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        item(key = "appearance") {
            val count = 4 + if (resolvedDark) 1 else 0
            SettingsGroup(stringResource(R.string.settings_group_general)) {
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
