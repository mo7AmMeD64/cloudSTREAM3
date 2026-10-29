package com.kodastream.app.ui.setup

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadLocations
import com.kodastream.app.ui.theme.APP_PALETTES
import com.kodastream.app.ui.theme.DYNAMIC_PALETTE_ID
import com.kodastream.app.ui.theme.ThemeViewModel

/**
 * The three first-run choices: language, accent color, download folder. The
 * color is saved as it is picked (ThemeViewModel); language and the "done"
 * flag are committed by [onFinish], which lets MainActivity swap to the app.
 */
@Composable
fun SetupWizard(
    themeViewModel: ThemeViewModel,
    onFinish: (languageTag: String) -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var language by rememberSaveable { mutableStateOf(AppPrefs.appLanguage(context)) }
    var downloadLocation by remember { mutableStateOf(DownloadLocations.readableLocation(context)) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (DownloadLocations.adoptTree(context, uri)) {
            downloadLocation = DownloadLocations.readableLocation(context)
        } else {
            Toast.makeText(context, R.string.download_folder_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    // Foreground is the full 108dp adaptive layer; scale 1.5x shows the 72dp visible window
                    modifier = Modifier.size(56.dp).scale(1.5f),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("cloud3", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.setup_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.setup_step_format, step + 1, 3),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(stepTitles[step]),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(stepSummaries[step]),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> LanguageStep(language) { language = it }
                1 -> ColorStep(themeViewModel)
                else -> FolderStep(
                    location = downloadLocation,
                    isCustom = AppPrefs.downloadTreeUri(context) != null,
                    onPick = { pickFolder.launch(null) },
                    onReset = {
                        AppPrefs.setDownloadTreeUri(context, null)
                        downloadLocation = DownloadLocations.readableLocation(context)
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text(stringResource(R.string.back))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (step < 2) step++ else onFinish(language)
            }) {
                Text(stringResource(if (step < 2) R.string.setup_next else R.string.setup_start))
            }
        }
    }
}

private val stepTitles = intArrayOf(
    R.string.setup_language_title,
    R.string.setup_color_title,
    R.string.setup_folder_title,
)

private val stepSummaries = intArrayOf(
    R.string.setup_language_summary,
    R.string.setup_color_summary,
    R.string.setup_folder_summary,
)

@Composable
private fun LanguageStep(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LanguageOption(
            label = stringResource(R.string.setup_language_system),
            selected = selected == AppPrefs.LANGUAGE_SYSTEM,
            onClick = { onSelect(AppPrefs.LANGUAGE_SYSTEM) },
        )
        LanguageOption(
            label = stringResource(R.string.setup_language_english),
            selected = selected == "en",
            onClick = { onSelect("en") },
        )
        LanguageOption(
            label = stringResource(R.string.setup_language_arabic),
            selected = selected == "ar",
            onClick = { onSelect("ar") },
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColorStep(themeViewModel: ThemeViewModel) {
    val selectedName = APP_PALETTES.firstOrNull { it.id == themeViewModel.palette }?.name
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                FilterChip(
                    selected = themeViewModel.palette == DYNAMIC_PALETTE_ID,
                    onClick = { themeViewModel.updatePalette(DYNAMIC_PALETTE_ID) },
                    label = { Text(stringResource(R.string.palette_wallpaper)) },
                )
            }
            items(APP_PALETTES) { palette ->
                val isSelected = themeViewModel.palette == palette.id
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(palette.seedPrimary, palette.seedTertiary)))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            themeViewModel.updatePalette(palette.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        Text(
            text = selectedName ?: stringResource(R.string.palette_wallpaper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderStep(
    location: String,
    isCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.download_location),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = location,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPick) {
                        Text(stringResource(R.string.download_location_change))
                    }
                    if (isCustom) {
                        TextButton(onClick = onReset) {
                            Text(stringResource(R.string.download_location_reset))
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.setup_folder_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
