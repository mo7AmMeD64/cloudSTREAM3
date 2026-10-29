package com.kodastream.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.ui.components.PosterCard
import com.kodastream.app.ui.components.PosterShelfSkeleton
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.lagradost.cloudstream3.SearchResponse

/**
 * One Home shelf as a full grid, painting the items Home already loaded and
 * asking for the next page near the end. Paging state lives in
 * [ExpandedHomeViewModel], so Back returns to Home untouched.
 */
@Composable
fun SectionScreen(
    viewModel: ExpandedHomeViewModel,
    onBack: () -> Unit,
    onOpenItem: (SearchResponse) -> Unit,
) {
    val sections by viewModel.sections.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val section = selected?.let(sections::get)
    if (section == null) {
        // The process was recreated and the in-memory section is gone.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    // Also fires when the grid is too short to scroll, so a small page fills.
    LaunchedEffect(nearEnd, section.hasNext, section.failed, section.loading) {
        if (nearEnd) viewModel.loadNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Spacer(Modifier.size(48.dp))
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(if (section.horizontal) 260.dp else 104.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            items(section.items, key = { it.url }) { item ->
                PosterCard(
                    item = item,
                    onClick = { onOpenItem(item) },
                    modifier = Modifier.fillMaxWidth(),
                    horizontal = section.horizontal,
                )
            }

            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                when {
                    section.loading -> SkeletonPulseProvider {
                        PosterShelfSkeleton(horizontal = section.horizontal)
                    }

                    section.failed -> TextButton(
                        onClick = { viewModel.loadNext(retry = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }

                    !section.hasNext -> Text(
                        text = stringResource(R.string.no_more_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
