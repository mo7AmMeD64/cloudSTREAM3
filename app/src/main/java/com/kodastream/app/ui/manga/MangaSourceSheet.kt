package com.kodastream.app.ui.manga

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.manga.SourceChoice
import com.kodastream.app.manga.groupSources
import java.util.Locale

/**
 * The source picker: pinned sources first, then the app language, then the
 * rest alphabetically, then sources without a language. The pin is stored in
 * manga prefs and never reorders the list while the sheet is open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MangaSourceSheet(
    sources: List<SourceChoice>,
    selectedId: Long?,
    pinnedIds: Set<Long>,
    iconUrlFor: (Long) -> String?,
    onSelect: (SourceChoice) -> Unit,
    onTogglePin: (SourceChoice, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appLanguage = remember { Locale.getDefault().language.ifBlank { "en" } }
    val otherLabel = stringResource(R.string.manga_sources_other)
    val groups = remember(sources, pinnedIds, appLanguage) {
        groupSources(
            sources = sources,
            appLanguage = appLanguage,
            pinnedIds = pinnedIds,
            otherLabel = otherLabel.lowercase(),
        )
    }
    val pinnedLabel = stringResource(R.string.manga_sources_pinned)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.manga_sources_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            groups.forEach { group ->
                stickyHeader(key = "header-${group.language}-${group.pinned}") {
                    GroupHeader(
                        text = when {
                            group.pinned -> pinnedLabel
                            group.language.isBlank() -> otherLabel
                            else -> group.language.uppercase()
                        },
                    )
                }
                items(group.sources, key = { "source-${it.id}" }) { choice ->
                    SourceRow(
                        choice = choice,
                        selected = choice.id == selectedId,
                        pinned = choice.id in pinnedIds,
                        iconUrl = iconUrlFor(choice.id),
                        onClick = { onSelect(choice) },
                        onTogglePin = { onTogglePin(choice, choice.id !in pinnedIds) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SourceRow(
    choice: SourceChoice,
    selected: Boolean,
    pinned: Boolean,
    iconUrl: String?,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaExtensionIconImage(
            url = iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = choice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (choice.nsfw) {
                    Spacer(Modifier.width(6.dp))
                    NsfwBadge()
                }
            }
            if (choice.language.isNotBlank()) {
                Text(
                    text = choice.language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(
                    if (pinned) R.string.manga_source_unpin else R.string.manga_source_pin
                ),
                tint = if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun NsfwBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(R.string.manga_source_18plus),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
