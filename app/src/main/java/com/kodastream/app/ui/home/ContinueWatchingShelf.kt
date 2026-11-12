package com.kodastream.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.HistoryStore.HistoryEntry
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.engine.Engine
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.SectionHeader

/**
 * Filters and sorts history entries for the "Continue Watching" shelf:
 * excludes completed titles and titles watched for <= 5 seconds,
 * orders newest first by last watched timestamp, and caps to at most 10.
 */
fun continueWatching(entries: List<HistoryEntry>): List<HistoryEntry> =
    entries
        .filter { !it.isComplete && it.positionMs > 5000L && it.durationMs > 0L }
        .sortedByDescending { it.lastWatchedAt }
        .take(10)

/**
 * A horizontal shelf displaying in-progress titles from watch history.
 * Reuses [PosterCard] with a wavy progress indicator underneath each card.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContinueWatchingShelf(
    items: List<HistoryEntry>,
    onOpenItem: (DetailsArgs) -> Unit,
    onResume: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.home_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = items,
                key = { it.key },
                contentType = { "continue_watching" },
            ) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    onClick = {
                        if (Engine.provider(entry.apiName) == null) {
                            onOpenItem(
                                DetailsArgs(
                                    apiName = entry.apiName,
                                    url = entry.url,
                                    title = entry.title,
                                    posterUrl = entry.posterUrl,
                                    posterHeaders = entry.posterHeaders,
                                )
                            )
                        } else {
                            onResume(
                                PlayerArgs(
                                    apiName = entry.apiName,
                                    data = entry.data,
                                    title = entry.title,
                                    subtitle = entry.subtitle,
                                    resumeKey = entry.resumeKey,
                                    history = entry,
                                )
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContinueWatchingCard(
    entry: HistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 128.dp,
) {
    Column(
        modifier = modifier.width(cardWidth),
    ) {
        PosterCard(
            name = entry.title,
            posterUrl = entry.posterUrl,
            posterHeaders = entry.posterHeaders,
            score10 = null,
            onClick = onClick,
            cardWidth = cardWidth,
        )
        Spacer(Modifier.height(6.dp))
        val progress = if (entry.durationMs > 0) {
            (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
