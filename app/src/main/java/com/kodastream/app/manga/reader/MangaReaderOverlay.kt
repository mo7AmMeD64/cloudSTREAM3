package com.kodastream.app.manga.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.kodastream.app.R
import com.kodastream.app.manga.MangaReaderOrientation
import com.kodastream.app.manga.MangaReaderSettings
import com.kodastream.app.manga.MangaReaderTheme
import com.kodastream.app.manga.MangaReadingMode
import com.kodastream.app.manga.MangaScaleType
import com.kodastream.app.manga.MangaZoomStart
import com.kodastream.app.manga.TapInvert
import com.kodastream.app.manga.TapZoneLayout
import kotlin.math.roundToInt

/**
 * Compose overlay over the ported View-based viewer. The viewer keeps all
 * gestures; this only draws the menu, the settings sheet and the error states.
 */
@Composable
fun MangaReaderOverlay(
    state: ReaderOverlayState,
    onClose: () -> Unit,
    onHideMenu: () -> Unit,
    onModeChange: (MangaReadingMode) -> Unit,
    onSettings: (MangaReaderSettings) -> Unit,
    onPageSelected: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onBookmark: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSharePage: () -> Unit,
    onSavePage: () -> Unit,
    onRetry: () -> Unit,
    onOpenWebView: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ReaderError(state, onRetry, onOpenWebView)

        AnimatedVisibility(
            visible = state.menuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
                    .clickable(onClick = onHideMenu),
            )
        }

        AnimatedVisibility(
            visible = state.menuVisible,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut() + shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                shrinkTowards = Alignment.Top,
            ),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                state = state,
                onClose = onClose,
                onBookmark = onBookmark,
                onOpenBrowser = onOpenBrowser,
                onSharePage = onSharePage,
                onSavePage = onSavePage,
            )
        }

        AnimatedVisibility(
            visible = state.menuVisible,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                expandFrom = Alignment.Bottom,
            ),
            exit = fadeOut() + shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                shrinkTowards = Alignment.Bottom,
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                state = state,
                onPageSelected = onPageSelected,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onSettings = onSettings,
                onModeChange = onModeChange,
            )
        }

        if (!state.menuVisible && state.settings.showPageNumber && state.pageCount > 0) {
            Text(
                text = stringResource(
                    R.string.manga_reader_page_counter,
                    state.page + 1,
                    state.pageCount,
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }

        if (state.loading && state.pageCount == 0 && state.error == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    state: ReaderOverlayState,
    onClose: () -> Unit,
    onBookmark: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSharePage: () -> Unit,
    onSavePage: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.mangaTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.chapterName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (state.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = stringResource(
                        if (state.bookmarked) R.string.manga_reader_bookmark_remove
                        else R.string.manga_reader_bookmark_add,
                    ),
                    tint = if (state.bookmarked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.manga_reader_more),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_reader_open_browser)) },
                        leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, contentDescription = null) },
                        onClick = {
                            menu = false
                            onOpenBrowser()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_reader_share_page)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        enabled = state.pageCount > 0,
                        onClick = {
                            menu = false
                            onSharePage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_reader_save_page)) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        enabled = state.pageCount > 0,
                        onClick = {
                            menu = false
                            onSavePage()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderOverlayState,
    onPageSelected: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSettings: (MangaReaderSettings) -> Unit,
    onModeChange: (MangaReadingMode) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val readingRtl = state.settings.mode.readingRtl
    val pages = state.pageCount.coerceAtLeast(1)
    var slider by remember(state.chapterUrl, state.pageCount) {
        mutableFloatStateOf(displayPage(state.page, pages, readingRtl).toFloat())
    }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.page, state.pageCount, state.chapterUrl) {
        if (!dragging) slider = displayPage(state.page, pages, readingRtl).toFloat()
    }

    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (state.pageCount > 1) {
                Slider(
                    value = slider,
                    onValueChange = {
                        dragging = true
                        slider = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onPageSelected(pageFromDisplay(slider.roundToInt(), pages, readingRtl))
                    },
                    valueRange = 0f..(pages - 1).toFloat(),
                    steps = (pages - 2).coerceAtLeast(0),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.manga_reader_page_counter,
                        state.page + 1,
                        state.pageCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                if (state.chapterIndex > 0) {
                    TextButton(onClick = onPreviousChapter) {
                        Text(stringResource(R.string.manga_reader_previous_chapter))
                    }
                }
                if (state.chapterIndex < state.chapterCount - 1) {
                    TextButton(onClick = onNextChapter) {
                        Text(stringResource(R.string.manga_reader_next_chapter))
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.manga_reader_settings),
                    )
                }
            }
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onModeChange = onModeChange,
            onSettings = onSettings,
        )
    }
}

@Composable
private fun ReaderError(
    state: ReaderOverlayState,
    onRetry: () -> Unit,
    onOpenWebView: () -> Unit,
) {
    val error = state.error ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (state.noPages) R.string.manga_reader_no_pages_title
                        else R.string.manga_reader_chapter_failed,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (state.noPages) {
                        stringResource(R.string.manga_reader_no_pages_hint)
                    } else {
                        error
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.noPages) {
                        FilledTonalButton(onClick = onOpenWebView) {
                            Text(stringResource(R.string.manga_reader_open_webview))
                        }
                    }
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.manga_reader_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    settings: MangaReaderSettings,
    onDismiss: () -> Unit,
    onModeChange: (MangaReadingMode) -> Unit,
    onSettings: (MangaReaderSettings) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.9f),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.manga_reader_settings),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_mode),
                    description = stringResource(R.string.manga_reader_setting_mode_desc),
                    value = modeLabel(settings.mode),
                    options = listOf(
                        ReaderChoice(MangaReadingMode.PAGED_RTL, stringResource(R.string.manga_reader_mode_paged_rtl)),
                        ReaderChoice(MangaReadingMode.PAGED_LTR, stringResource(R.string.manga_reader_mode_paged_ltr)),
                        ReaderChoice(MangaReadingMode.PAGED_VERTICAL, stringResource(R.string.manga_reader_mode_paged_vertical)),
                        ReaderChoice(MangaReadingMode.WEBTOON, stringResource(R.string.manga_reader_mode_webtoon)),
                        ReaderChoice(MangaReadingMode.WEBTOON_GAPS, stringResource(R.string.manga_reader_mode_webtoon_gaps)),
                    ),
                    onSelected = onModeChange,
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_tap),
                    description = stringResource(R.string.manga_reader_setting_tap_desc),
                    value = tapLabel(settings.tapLayout),
                    options = listOf(
                        ReaderChoice(TapZoneLayout.RIGHT_AND_LEFT, stringResource(R.string.manga_reader_tap_right_left)),
                        ReaderChoice(TapZoneLayout.L_SHAPED, stringResource(R.string.manga_reader_tap_l)),
                        ReaderChoice(TapZoneLayout.KINDLISH, stringResource(R.string.manga_reader_tap_kindlish)),
                        ReaderChoice(TapZoneLayout.EDGE, stringResource(R.string.manga_reader_tap_edge)),
                        ReaderChoice(TapZoneLayout.DISABLED, stringResource(R.string.manga_reader_tap_disabled)),
                    ),
                    onSelected = { onSettings(settings.copy(tapLayout = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_invert),
                    description = stringResource(R.string.manga_reader_setting_invert_desc),
                    value = invertLabel(settings.tapInvert),
                    options = listOf(
                        ReaderChoice(TapInvert.NONE, stringResource(R.string.manga_reader_invert_none)),
                        ReaderChoice(TapInvert.HORIZONTAL, stringResource(R.string.manga_reader_invert_horizontal)),
                        ReaderChoice(TapInvert.VERTICAL, stringResource(R.string.manga_reader_invert_vertical)),
                        ReaderChoice(TapInvert.BOTH, stringResource(R.string.manga_reader_invert_both)),
                    ),
                    onSelected = { onSettings(settings.copy(tapInvert = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_scale),
                    description = stringResource(R.string.manga_reader_setting_scale_desc),
                    value = scaleLabel(settings.scaleType),
                    options = listOf(
                        ReaderChoice(MangaScaleType.FIT_SCREEN, stringResource(R.string.manga_reader_scale_fit_screen)),
                        ReaderChoice(MangaScaleType.FIT_WIDTH, stringResource(R.string.manga_reader_scale_fit_width)),
                        ReaderChoice(MangaScaleType.FIT_HEIGHT, stringResource(R.string.manga_reader_scale_fit_height)),
                        ReaderChoice(MangaScaleType.ORIGINAL, stringResource(R.string.manga_reader_scale_original)),
                        ReaderChoice(MangaScaleType.SMART_FIT, stringResource(R.string.manga_reader_scale_smart)),
                    ),
                    onSelected = { onSettings(settings.copy(scaleType = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_zoom),
                    description = stringResource(R.string.manga_reader_setting_zoom_desc),
                    value = zoomLabel(settings.zoomStart),
                    options = listOf(
                        ReaderChoice(MangaZoomStart.AUTOMATIC, stringResource(R.string.manga_reader_zoom_automatic)),
                        ReaderChoice(MangaZoomStart.LEFT, stringResource(R.string.manga_reader_zoom_left)),
                        ReaderChoice(MangaZoomStart.CENTER, stringResource(R.string.manga_reader_zoom_center)),
                        ReaderChoice(MangaZoomStart.RIGHT, stringResource(R.string.manga_reader_zoom_right)),
                    ),
                    onSelected = { onSettings(settings.copy(zoomStart = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_crop),
                    description = stringResource(R.string.manga_reader_setting_crop_desc),
                    checked = settings.cropBorders,
                    onCheckedChange = { onSettings(settings.copy(cropBorders = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_transitions),
                    description = stringResource(R.string.manga_reader_setting_transitions_desc),
                    checked = settings.pageTransitions,
                    onCheckedChange = { onSettings(settings.copy(pageTransitions = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_theme),
                    description = stringResource(R.string.manga_reader_setting_theme_desc),
                    value = themeLabel(settings.theme),
                    options = listOf(
                        ReaderChoice(MangaReaderTheme.BLACK, stringResource(R.string.manga_reader_theme_black)),
                        ReaderChoice(MangaReaderTheme.WHITE, stringResource(R.string.manga_reader_theme_white)),
                        ReaderChoice(MangaReaderTheme.GRAY, stringResource(R.string.manga_reader_theme_gray)),
                        ReaderChoice(MangaReaderTheme.AUTO, stringResource(R.string.manga_reader_theme_auto)),
                    ),
                    onSelected = { onSettings(settings.copy(theme = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_keep_on),
                    description = stringResource(R.string.manga_reader_setting_keep_on_desc),
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onSettings(settings.copy(keepScreenOn = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_page_number),
                    description = stringResource(R.string.manga_reader_setting_page_number_desc),
                    checked = settings.showPageNumber,
                    onCheckedChange = { onSettings(settings.copy(showPageNumber = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_long_tap),
                    description = stringResource(R.string.manga_reader_setting_long_tap_desc),
                    checked = settings.longTapSave,
                    onCheckedChange = { onSettings(settings.copy(longTapSave = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_volume),
                    description = stringResource(R.string.manga_reader_setting_volume_desc),
                    checked = settings.volumeKeys,
                    onCheckedChange = { onSettings(settings.copy(volumeKeys = it)) },
                )
            }
            item {
                SliderRow(
                    title = stringResource(R.string.manga_reader_setting_padding),
                    description = stringResource(R.string.manga_reader_setting_padding_desc),
                    value = settings.webtoonPadding,
                    onValueChange = { onSettings(settings.copy(webtoonPadding = it)) },
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.manga_reader_setting_double_tap),
                    description = stringResource(R.string.manga_reader_setting_double_tap_desc),
                    checked = settings.doubleTapZoom,
                    onCheckedChange = { onSettings(settings.copy(doubleTapZoom = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.manga_reader_setting_orientation),
                    description = stringResource(R.string.manga_reader_setting_orientation_desc),
                    value = orientationLabel(settings.orientation),
                    options = listOf(
                        ReaderChoice(MangaReaderOrientation.FREE, stringResource(R.string.manga_reader_orientation_free)),
                        ReaderChoice(MangaReaderOrientation.PORTRAIT, stringResource(R.string.manga_reader_orientation_portrait)),
                        ReaderChoice(MangaReaderOrientation.LANDSCAPE, stringResource(R.string.manga_reader_orientation_landscape)),
                    ),
                    onSelected = { onSettings(settings.copy(orientation = it)) },
                )
            }
        }
    }
}

private data class ReaderChoice<T>(val value: T, val label: String)

@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    value: String,
    options: List<ReaderChoice<T>>,
    onSelected: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, description, onClick = { open = true }) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    onSelected(option.value)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option.label == value,
                                onClick = {
                                    open = false
                                    onSelected(option.value)
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(title, description, onClick = { onCheckedChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    SettingRow(title, description + " " + value + "%") {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..30f,
            steps = 29,
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

private fun displayPage(page: Int, pages: Int, rtl: Boolean): Int =
    if (rtl) pages - 1 - page else page

private fun pageFromDisplay(value: Int, pages: Int, rtl: Boolean): Int =
    if (rtl) pages - 1 - value else value

@Composable
private fun modeLabel(mode: MangaReadingMode): String = stringResource(
    when (mode) {
        MangaReadingMode.PAGED_RTL -> R.string.manga_reader_mode_paged_rtl
        MangaReadingMode.PAGED_LTR -> R.string.manga_reader_mode_paged_ltr
        MangaReadingMode.PAGED_VERTICAL -> R.string.manga_reader_mode_paged_vertical
        MangaReadingMode.WEBTOON -> R.string.manga_reader_mode_webtoon
        MangaReadingMode.WEBTOON_GAPS -> R.string.manga_reader_mode_webtoon_gaps
    },
)

@Composable
private fun tapLabel(layout: TapZoneLayout): String = stringResource(
    when (layout) {
        TapZoneLayout.RIGHT_AND_LEFT -> R.string.manga_reader_tap_right_left
        TapZoneLayout.L_SHAPED -> R.string.manga_reader_tap_l
        TapZoneLayout.KINDLISH -> R.string.manga_reader_tap_kindlish
        TapZoneLayout.EDGE -> R.string.manga_reader_tap_edge
        TapZoneLayout.DISABLED -> R.string.manga_reader_tap_disabled
    },
)

@Composable
private fun invertLabel(invert: TapInvert): String = stringResource(
    when (invert) {
        TapInvert.NONE -> R.string.manga_reader_invert_none
        TapInvert.HORIZONTAL -> R.string.manga_reader_invert_horizontal
        TapInvert.VERTICAL -> R.string.manga_reader_invert_vertical
        TapInvert.BOTH -> R.string.manga_reader_invert_both
    },
)

@Composable
private fun scaleLabel(scale: MangaScaleType): String = stringResource(
    when (scale) {
        MangaScaleType.FIT_SCREEN -> R.string.manga_reader_scale_fit_screen
        MangaScaleType.FIT_WIDTH -> R.string.manga_reader_scale_fit_width
        MangaScaleType.FIT_HEIGHT -> R.string.manga_reader_scale_fit_height
        MangaScaleType.ORIGINAL -> R.string.manga_reader_scale_original
        MangaScaleType.SMART_FIT -> R.string.manga_reader_scale_smart
    },
)

@Composable
private fun zoomLabel(zoom: MangaZoomStart): String = stringResource(
    when (zoom) {
        MangaZoomStart.AUTOMATIC -> R.string.manga_reader_zoom_automatic
        MangaZoomStart.LEFT -> R.string.manga_reader_zoom_left
        MangaZoomStart.CENTER -> R.string.manga_reader_zoom_center
        MangaZoomStart.RIGHT -> R.string.manga_reader_zoom_right
    },
)

@Composable
private fun themeLabel(theme: MangaReaderTheme): String = stringResource(
    when (theme) {
        MangaReaderTheme.BLACK -> R.string.manga_reader_theme_black
        MangaReaderTheme.WHITE -> R.string.manga_reader_theme_white
        MangaReaderTheme.GRAY -> R.string.manga_reader_theme_gray
        MangaReaderTheme.AUTO -> R.string.manga_reader_theme_auto
    },
)

@Composable
private fun orientationLabel(orientation: MangaReaderOrientation): String = stringResource(
    when (orientation) {
        MangaReaderOrientation.FREE -> R.string.manga_reader_orientation_free
        MangaReaderOrientation.PORTRAIT -> R.string.manga_reader_orientation_portrait
        MangaReaderOrientation.LANDSCAPE -> R.string.manga_reader_orientation_landscape
    },
)
