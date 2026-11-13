package com.kodastream.app.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.ui.home.hero.HeroStyle
import com.kodastream.app.ui.theme.APP_PALETTES
import com.kodastream.app.ui.theme.DYNAMIC_PALETTE_ID
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel
import kotlin.math.roundToInt

@Composable
internal fun AppearanceSettingsPage(
    themeViewModel: ThemeViewModel,
    onOpenColors: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var navPosition by remember { mutableStateOf(AppPrefs.navPosition(context)) }
    var navBarStyle by remember { mutableStateOf(AppPrefs.navBarStyle(context)) }
    var hideStatusBar by remember { mutableStateOf(AppPrefs.hideStatusBar(context)) }
    var hidePosterTitles by remember { mutableStateOf(AppPrefs.hidePosterTitles(context)) }
    var navBarIconSize by remember { mutableStateOf(AppPrefs.navBarIconSize(context)) }
    var navBarWidth by remember { mutableStateOf(AppPrefs.navBarWidth(context)) }
    var navBarHeight by remember { mutableStateOf(AppPrefs.navBarHeight(context)) }
    var uiStyle by remember { mutableStateOf(AppPrefs.uiStyle(context)) }
    var appLanguage by remember { mutableStateOf(AppPrefs.appLanguage(context)) }
    var languageDialog by remember { mutableStateOf(false) }
    var heroStyle by remember { mutableStateOf(HeroStyle.parse(AppPrefs.homeHeroStyle(context))) }
    var heroDialog by remember { mutableStateOf(false) }

    val resolvedDark = when (themeViewModel.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        item(key = "appearance") {
            val count = 8 + if (resolvedDark) 1 else 0
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
                SwitchRow(
                    index = 1,
                    count = count,
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.settings_hide_status_bar),
                    supporting = stringResource(R.string.settings_hide_status_bar_summary),
                    checked = hideStatusBar,
                    onCheckedChange = { checked ->
                        AppPrefs.setHideStatusBar(context, checked)
                        hideStatusBar = checked
                    },
                )
                SwitchRow(
                    index = 2,
                    count = count,
                    icon = Icons.Rounded.Title,
                    title = stringResource(R.string.settings_hide_titles),
                    supporting = stringResource(R.string.settings_hide_titles_summary),
                    checked = hidePosterTitles,
                    onCheckedChange = { checked ->
                        AppPrefs.setHidePosterTitles(context, checked)
                        hidePosterTitles = checked
                    },
                )
                NavigationRow(
                    index = 3,
                    count = count,
                    icon = Icons.Rounded.Translate,
                    title = stringResource(R.string.setup_language_title),
                    supporting = languageLabel(appLanguage),
                    onClick = { languageDialog = true },
                )
                if (resolvedDark) {
                    SwitchRow(
                        index = 4,
                        count = count,
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.settings_amoled),
                        supporting = stringResource(R.string.settings_amoled_summary),
                        checked = themeViewModel.amoled,
                        onCheckedChange = themeViewModel::updateAmoled,
                    )
                }
                ChoiceRow(
                    index = if (resolvedDark) 5 else 4,
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
                ChoiceRow(
                    index = if (resolvedDark) 6 else 5,
                    count = count,
                    icon = Icons.Rounded.Style,
                    title = stringResource(R.string.settings_ui_style),
                    options = listOf(AppPrefs.UI_STYLE_MATERIAL, AppPrefs.UI_STYLE_GLASS),
                    selected = uiStyle,
                    label = { style ->
                        if (style == AppPrefs.UI_STYLE_GLASS) {
                            stringResource(R.string.settings_ui_style_glass)
                        } else {
                            stringResource(R.string.settings_ui_style_material)
                        }
                    },
                    onSelect = { style ->
                        AppPrefs.setUiStyle(context, style)
                        uiStyle = style
                    },
                )
                ChoiceRow(
                    index = if (resolvedDark) 7 else 6,
                    count = count,
                    icon = Icons.Rounded.Style,
                    title = stringResource(R.string.settings_nav_bar_style),
                    options = listOf(AppPrefs.NAV_BAR_CLOUD, AppPrefs.NAV_BAR_NUVIO),
                    selected = navBarStyle,
                    label = { style ->
                        stringResource(
                            if (style == AppPrefs.NAV_BAR_NUVIO) {
                                R.string.nav_bar_style_nuvio
                            } else {
                                R.string.nav_bar_style_cloud
                            }
                        )
                    },
                    onSelect = { style ->
                        AppPrefs.setNavBarStyle(context, style)
                        navBarStyle = style
                    },
                )
                NavigationRow(
                    index = if (resolvedDark) 8 else 7,
                    count = count,
                    icon = Icons.Rounded.ViewCarousel,
                    title = stringResource(R.string.settings_home_hero),
                    supporting = stringResource(heroStyle.labelRes),
                    onClick = { heroDialog = true },
                )
            }
        }

        if (navBarStyle == AppPrefs.NAV_BAR_NUVIO && navPosition == AppPrefs.NAV_BOTTOM) {
            item(key = "nav_bar_tuning") {
                SettingsGroup(stringResource(R.string.settings_nav_bar_tuning)) {
                    SliderRow(
                        index = 0,
                        count = 3,
                        icon = Icons.Rounded.PhotoSizeSelectLarge,
                        title = stringResource(R.string.settings_nav_bar_icon_size),
                        valueText = stringResource(R.string.settings_nav_bar_dp, navBarIconSize),
                        value = navBarIconSize.toFloat(),
                        valueRange = 20f..34f,
                        steps = 13,
                        onValueChange = { navBarIconSize = it.roundToInt() },
                        onValueChangeFinished = {
                            AppPrefs.setNavBarIconSize(context, navBarIconSize)
                        },
                    )
                    SliderRow(
                        index = 1,
                        count = 3,
                        icon = Icons.Rounded.AspectRatio,
                        title = stringResource(R.string.settings_nav_bar_width),
                        valueText = stringResource(R.string.settings_nav_bar_dp, navBarWidth),
                        value = navBarWidth.toFloat(),
                        valueRange = 240f..480f,
                        steps = 11,
                        onValueChange = { navBarWidth = it.roundToInt() },
                        onValueChangeFinished = {
                            AppPrefs.setNavBarWidth(context, navBarWidth)
                        },
                    )
                    SliderRow(
                        index = 2,
                        count = 3,
                        icon = Icons.Rounded.Height,
                        title = stringResource(R.string.settings_nav_bar_height),
                        valueText = stringResource(R.string.settings_nav_bar_dp, navBarHeight),
                        value = navBarHeight.toFloat(),
                        valueRange = 48f..84f,
                        steps = 8,
                        onValueChange = { navBarHeight = it.roundToInt() },
                        onValueChangeFinished = {
                            AppPrefs.setNavBarHeight(context, navBarHeight)
                        },
                    )
                }
            }
        }

        item(key = "colors") {
            SettingsGroup(stringResource(R.string.settings_colors)) {
                NavigationRow(
                    index = 0,
                    count = 1,
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.color_palette),
                    supporting = if (themeViewModel.palette == DYNAMIC_PALETTE_ID) {
                        stringResource(R.string.dynamic_from_wallpaper)
                    } else {
                        APP_PALETTES.firstOrNull { it.id == themeViewModel.palette }?.name
                            ?: stringResource(R.string.dynamic_from_wallpaper)
                    },
                    onClick = onOpenColors,
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

