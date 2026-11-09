package com.kodastream.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kodastream.app.R
import com.kodastream.app.manga.MangaPageCache
import com.kodastream.app.manga.MangaPageListStore
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaReadingMode
import com.kodastream.app.manga.TapInvert
import com.kodastream.app.manga.TapZoneLayout
import com.kodastream.app.manga.store.MangaStoreRepository
import com.kodastream.app.ui.manga.MangaLanguageDialog
import com.kodastream.app.ui.manga.mangaLanguageSortKey
import com.kodastream.app.ui.manga.mangaSourceLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun modeReaderLabel(mode: MangaReadingMode): String = stringResource(
    when (mode) {
        MangaReadingMode.PAGED_RTL -> R.string.manga_reader_mode_paged_rtl
        MangaReadingMode.PAGED_LTR -> R.string.manga_reader_mode_paged_ltr
        MangaReadingMode.PAGED_VERTICAL -> R.string.manga_reader_mode_paged_vertical
        MangaReadingMode.WEBTOON -> R.string.manga_reader_mode_webtoon
        MangaReadingMode.WEBTOON_GAPS -> R.string.manga_reader_mode_webtoon_gaps
    },
)

@Composable
private fun tapReaderLabel(layout: TapZoneLayout): String = stringResource(
    when (layout) {
        TapZoneLayout.RIGHT_AND_LEFT -> R.string.manga_reader_tap_right_left
        TapZoneLayout.L_SHAPED -> R.string.manga_reader_tap_l
        TapZoneLayout.KINDLISH -> R.string.manga_reader_tap_kindlish
        TapZoneLayout.EDGE -> R.string.manga_reader_tap_edge
        TapZoneLayout.DISABLED -> R.string.manga_reader_tap_disabled
    },
)

@Composable
private fun invertReaderLabel(invert: TapInvert): String = stringResource(
    when (invert) {
        TapInvert.NONE -> R.string.manga_reader_invert_none
        TapInvert.HORIZONTAL -> R.string.manga_reader_invert_horizontal
        TapInvert.VERTICAL -> R.string.manga_reader_invert_vertical
        TapInvert.BOTH -> R.string.manga_reader_invert_both
    },
)

@Composable
internal fun MangaSettingsPage(
    onBack: () -> Unit,
    onOpenMangaExtensions: () -> Unit,
    onOpenMangaStores: () -> Unit,
    onMangaSectionChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settingsScope = rememberCoroutineScope()
    val appLocale = LocalConfiguration.current.locales[0]

    var mangaAllLanguages by remember { mutableStateOf(MangaPrefs.allLanguages(context)) }
    var mangaLanguages by remember { mutableStateOf(MangaPrefs.languages(context)) }
    var mangaLanguageDialog by remember { mutableStateOf(false) }
    var mangaSection by remember { mutableStateOf(MangaPrefs.mangaSectionEnabled(context)) }
    var cachedMangaLanguages by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) {
            MangaStoreRepository.cachedExtensions(context).map { it.lang }
        }
        cachedMangaLanguages = mangaSourceLanguages(cached)
            .sortedBy { mangaLanguageSortKey(it, appLocale) }
    }
    val mangaLanguageOptions = remember(cachedMangaLanguages, mangaLanguages, appLocale) {
        (cachedMangaLanguages + mangaLanguages)
            .distinct()
            .sortedBy { mangaLanguageSortKey(it, appLocale) }
    }

    val readerDefaults = remember {
        MangaPrefs.readerSettings(context, 0L, "")
    }
    var readerMode by remember { mutableStateOf(readerDefaults.mode) }
    var readerTap by remember { mutableStateOf(readerDefaults.tapLayout) }
    var readerInvert by remember { mutableStateOf(readerDefaults.tapInvert) }
    var readerKeepOn by remember { mutableStateOf(readerDefaults.keepScreenOn) }
    var readerPageNumber by remember { mutableStateOf(readerDefaults.showPageNumber) }
    var readerVolume by remember { mutableStateOf(readerDefaults.volumeKeys) }
    var readerCacheMb by remember { mutableStateOf(MangaPrefs.readerCacheMb(context)) }
    var cacheUsed by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        // The first call walks the whole page cache; keep it off the UI thread.
        cacheUsed = withContext(Dispatchers.IO) { MangaPageCache.usedBytes(context) }
    }
    var checkOnOpen by remember { mutableStateOf(MangaPrefs.checkUpdatesOnOpen(context)) }
    var nsfwGlobal by remember { mutableStateOf(MangaPrefs.showNsfwGlobalSearch(context)) }
    val directionShown = readerMode == MangaReadingMode.PAGED_RTL ||
        readerMode == MangaReadingMode.PAGED_LTR

    SettingsPageScaffold(
        title = stringResource(R.string.settings_manga),
        onBack = onBack,
    ) {
        item(key = "manga_section") {
            val count = 4
            var row = -1
            SettingsGroup(stringResource(R.string.settings_manga_group_sources)) {
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.AutoStories,
                    title = stringResource(R.string.settings_manga_section),
                    supporting = stringResource(R.string.settings_manga_section_summary),
                    checked = mangaSection,
                    onCheckedChange = { value ->
                        MangaPrefs.setMangaSectionEnabled(context, value)
                        mangaSection = value
                        onMangaSectionChanged(value)
                    },
                )
                NavigationRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Extension,
                    title = stringResource(R.string.manga_extensions_title),
                    supporting = stringResource(R.string.settings_manga_extensions_summary),
                    onClick = onOpenMangaExtensions,
                )
                NavigationRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Dns,
                    title = stringResource(R.string.manga_stores_title),
                    supporting = stringResource(R.string.settings_manga_stores_summary),
                    onClick = onOpenMangaStores,
                )
                NavigationRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Translate,
                    title = stringResource(R.string.manga_extension_languages),
                    supporting = if (mangaAllLanguages) {
                        stringResource(R.string.manga_language_all)
                    } else {
                        stringResource(R.string.manga_language_selected_n, mangaLanguages.size)
                    },
                    onClick = { mangaLanguageDialog = true },
                )
            }
        }

        item(key = "manga_reader") {
            val count = 6 + if (directionShown) 1 else 0
            var row = -1
            SettingsGroup(stringResource(R.string.settings_manga_group_reader)) {
                ChoiceRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.AutoStories,
                    title = stringResource(R.string.settings_manga_reader_mode),
                    options = MangaReadingMode.entries,
                    selected = readerMode,
                    label = { modeReaderLabel(it) },
                    onSelect = { mode ->
                        readerMode = mode
                        MangaPrefs.setReaderMode(context, mode)
                    },
                )
                if (directionShown) {
                    ChoiceRow(
                        index = ++row,
                        count = count,
                        icon = Icons.Rounded.SwapHoriz,
                        title = stringResource(R.string.settings_manga_reader_direction),
                        options = listOf(
                            MangaReadingMode.PAGED_RTL,
                            MangaReadingMode.PAGED_LTR,
                        ),
                        selected = readerMode,
                        label = {
                            stringResource(
                                if (it == MangaReadingMode.PAGED_RTL) {
                                    R.string.manga_reader_direction_rtl
                                } else {
                                    R.string.manga_reader_direction_ltr
                                },
                            )
                        },
                        onSelect = { mode ->
                            readerMode = mode
                            MangaPrefs.setReaderMode(context, mode)
                        },
                    )
                }
                ChoiceRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.manga_reader_setting_tap),
                    options = TapZoneLayout.entries,
                    selected = readerTap,
                    label = { tapReaderLabel(it) },
                    onSelect = { value ->
                        readerTap = value
                        MangaPrefs.setReaderTapLayout(context, value)
                    },
                )
                ChoiceRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.manga_reader_setting_invert),
                    options = TapInvert.entries,
                    selected = readerInvert,
                    label = { invertReaderLabel(it) },
                    onSelect = { value ->
                        readerInvert = value
                        MangaPrefs.setReaderInvert(context, value)
                    },
                )
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Contrast,
                    title = stringResource(R.string.manga_reader_setting_keep_on),
                    supporting = stringResource(R.string.manga_reader_setting_keep_on_desc),
                    checked = readerKeepOn,
                    onCheckedChange = { value ->
                        readerKeepOn = value
                        MangaPrefs.setReaderKeepScreenOn(context, value)
                    },
                )
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Visibility,
                    title = stringResource(R.string.manga_reader_setting_page_number),
                    supporting = stringResource(R.string.manga_reader_setting_page_number_desc),
                    checked = readerPageNumber,
                    onCheckedChange = { value ->
                        readerPageNumber = value
                        MangaPrefs.setReaderShowPageNumber(context, value)
                    },
                )
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.VolumeUp,
                    title = stringResource(R.string.manga_reader_setting_volume),
                    supporting = stringResource(R.string.manga_reader_setting_volume_desc),
                    checked = readerVolume,
                    onCheckedChange = { value ->
                        readerVolume = value
                        MangaPrefs.setReaderVolumeKeys(context, value)
                    },
                )
            }
        }

        item(key = "manga_data") {
            val count = 4
            var row = -1
            SettingsGroup(stringResource(R.string.settings_manga_group_data)) {
                ChoiceRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.settings_manga_cache_size),
                    options = listOf(100, 200, 500, 1000),
                    selected = readerCacheMb,
                    label = { stringResource(R.string.manga_cache_mb, it) },
                    onSelect = { value ->
                        readerCacheMb = value
                        MangaPrefs.setReaderCacheMb(context, value)
                    },
                )
                NavigationRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.DeleteSweep,
                    title = stringResource(R.string.settings_manga_cache_clear),
                    supporting = stringResource(
                        R.string.settings_manga_cache_used,
                        android.text.format.Formatter.formatShortFileSize(context, cacheUsed),
                    ),
                    onClick = {
                        settingsScope.launch {
                            withContext(Dispatchers.IO) {
                                MangaPageCache.clear(context)
                                MangaPageListStore.clear(context)
                            }
                            cacheUsed = 0
                            Toast.makeText(
                                context,
                                R.string.settings_manga_cache_cleared,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Update,
                    title = stringResource(R.string.settings_manga_check_on_open),
                    supporting = stringResource(R.string.settings_manga_check_on_open_summary),
                    checked = checkOnOpen,
                    onCheckedChange = { value ->
                        checkOnOpen = value
                        MangaPrefs.setCheckUpdatesOnOpen(context, value)
                    },
                )
                SwitchRow(
                    index = ++row,
                    count = count,
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.settings_manga_nsfw_global),
                    supporting = stringResource(R.string.settings_manga_nsfw_global_summary),
                    checked = nsfwGlobal,
                    onCheckedChange = { value ->
                        nsfwGlobal = value
                        MangaPrefs.setShowNsfwGlobalSearch(context, value)
                    },
                )
            }
        }
    }

    if (mangaLanguageDialog) {
        MangaLanguageDialog(
            languages = mangaLanguageOptions,
            selected = mangaLanguages,
            allLanguages = mangaAllLanguages,
            onDismiss = { mangaLanguageDialog = false },
            onSave = { selected, all ->
                mangaAllLanguages = all
                mangaLanguages = selected
                MangaPrefs.setAllLanguages(context, all)
                MangaPrefs.setLanguages(context, selected)
            },
        )
    }
}
