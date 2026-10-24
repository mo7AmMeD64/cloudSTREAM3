package com.lagradost.cloudstream3.ui.subtitles

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.DialogFragment
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.kodastream.app.R
import com.kodastream.app.ui.theme.KodaStreamTheme
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.setSubtitleViewStyle
import java.io.File

/**
 * The player's subtitle style editor. It edits the same [SaveCaptionStyle] the
 * CloudStream player already reads, so applying it updates the running player
 * immediately and survives the next restart.
 */
class SubtitleStyleDialog : DialogFragment() {
    private val pickedFontPath = mutableStateOf<String?>(null)

    private val fontPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ctx = context ?: return@registerForActivityResult
        val target = File(ctx.filesDir, "customfont.ttf")
        val temp = File(ctx.cacheDir, "customfont.tmp")
        val path = runCatching {
            temp.delete()
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("No input stream")
            Typeface.createFromFile(temp)
            target.delete()
            check(temp.renameTo(target)) { "Could not save font" }
            target.absolutePath
        }.onFailure {
            temp.delete()
        }.getOrNull()

        pickedFontPath.value = path
        if (path == null) {
            Toast.makeText(ctx, R.string.subtitle_font_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogFullscreenPlayer)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireActivity()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            KodaStreamTheme {
                SubtitleStyleEditor(
                    initial = SubtitlesFragment.getCurrentSavedStyle(),
                    pickedFontPath = pickedFontPath,
                    onPickFont = {
                        fontPicker.launch(
                            arrayOf(
                                "font/*",
                                "application/x-font-ttf",
                                "application/x-font-opentype",
                            )
                        )
                    },
                    onApply = { style ->
                        SubtitlesFragment.applyAndSaveStyle(requireContext(), style)
                        dismiss()
                    },
                    onCancel = { dismiss() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

private val textColors = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFFF00.toInt(),
    0xFF00FFFF.toInt(),
    0xFF00FF00.toInt(),
    0xFFFF6060.toInt(),
    0xFF000000.toInt(),
)

private val backgroundColors = listOf(
    0x00000000,
    0x66000000,
    0xFF000000.toInt(),
    0xFFFFFFFF.toInt(),
)

private val edgeColors = listOf(
    0xFF000000.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFFFFF00.toInt(),
)

@Composable
private fun SubtitleStyleEditor(
    initial: SaveCaptionStyle,
    pickedFontPath: MutableState<String?>,
    onPickFont: () -> Unit,
    onApply: (SaveCaptionStyle) -> Unit,
    onCancel: () -> Unit,
) {
    var style by remember { mutableStateOf(initial) }

    LaunchedEffect(pickedFontPath.value) {
        pickedFontPath.value?.let { path ->
            style = style.copy(font = SubtitleFont.Custom, typefaceFilePath = path)
            pickedFontPath.value = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Text(
            text = stringResource(R.string.subtitles_style),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SubtitlePreview(style) }

            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.subtitle_size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(style.fixedTextSize ?: 25f).toInt()}sp",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = style.fixedTextSize ?: 25f,
                        onValueChange = { style = style.copy(fixedTextSize = it) },
                        valueRange = 10f..60f,
                    )
                }
            }

            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_text_color),
                    swatches = textColors,
                    selected = style.foregroundColor,
                ) { color -> style = style.copy(foregroundColor = color) }
            }
            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_bg_color),
                    swatches = backgroundColors,
                    selected = style.backgroundColor,
                ) { color -> style = style.copy(backgroundColor = color) }
            }
            item {
                SwatchRow(
                    label = stringResource(R.string.subtitle_edge_color),
                    swatches = edgeColors,
                    selected = style.edgeColor,
                ) { color -> style = style.copy(edgeColor = color) }
            }

            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.subtitle_edge),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE,
                                onClick = { style = style.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_NONE) },
                                label = { Text(stringResource(R.string.edge_none)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                onClick = { style = style.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE) },
                                label = { Text(stringResource(R.string.edge_outline)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                onClick = { style = style.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) },
                                label = { Text(stringResource(R.string.edge_shadow)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.edgeType == CaptionStyleCompat.EDGE_TYPE_RAISED,
                                onClick = { style = style.copy(edgeType = CaptionStyleCompat.EDGE_TYPE_RAISED) },
                                label = { Text(stringResource(R.string.edge_raised)) },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.subtitle_font),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = style.font == null && style.typefaceFilePath == null,
                                onClick = {
                                    style = style.copy(font = null, typefaceFilePath = null)
                                },
                                label = { Text(stringResource(R.string.font_default)) },
                            )
                        }
                        item {
                            FilterChip(
                                selected = style.typefaceFilePath != null,
                                onClick = onPickFont,
                                label = { Text(stringResource(R.string.font_import)) },
                            )
                        }
                        items(SubtitleFont.entries.filter { it.resource != 0 }) { font ->
                            FilterChip(
                                selected = style.font == font && style.typefaceFilePath == null,
                                onClick = {
                                    style = style.copy(font = font, typefaceFilePath = null)
                                },
                                label = { Text(font.label) },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { style = SubtitlesFragment.defaultSubtitleStyle }) {
                Text(stringResource(R.string.subtitle_reset))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.sort_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onApply(style) }) {
                Text(stringResource(R.string.sort_apply))
            }
        }
    }
}

@Composable
private fun SubtitlePreview(style: SaveCaptionStyle) {
    val previewText = stringResource(R.string.subtitle_preview)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(110.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF202020)),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SubtitleView(context).apply {
                    setApplyEmbeddedStyles(false)
                }
            },
            update = { view ->
                setSubtitleViewStyle(view, style, false)
                view.setCues(
                    listOf(
                        Cue.Builder()
                            .setText(SpannableString(previewText))
                            .applyStyle(style)
                            .build()
                    )
                )
            },
        )
    }
}

@Composable
private fun SwatchRow(
    label: String,
    swatches: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(swatches) { color ->
                val isSelected = color == selected
                val isTransparent = (color ushr 24) == 0
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(if (isTransparent) Color.Transparent else Color(color))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onPick(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isTransparent) {
                        Text(
                            text = stringResource(R.string.none),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
