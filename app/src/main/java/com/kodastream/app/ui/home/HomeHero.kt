package com.kodastream.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodastream.app.R
import com.kodastream.app.ui.components.RemoteImage
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * The Home hero: one large focused poster with its neighbours peeking in as
 * slivers. Fed entirely from the first loaded shelf, so it costs no extra
 * network request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeHero(
    items: List<SearchResponse>,
    onOpen: (SearchResponse) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            HorizontalCenteredHeroCarousel(
                state = rememberCarouselState { items.size },
                itemSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) { index ->
                HeroCard(
                    item = items[index],
                    onClick = { onOpen(items[index]) },
                )
            }
        } else {
            HorizontalMultiBrowseCarousel(
                state = rememberCarouselState { items.size },
                preferredItemWidth = 320.dp,
                itemSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
            ) { index ->
                HeroCard(
                    item = items[index],
                    onClick = { onOpen(items[index]) },
                )
            }
        }
    }
}

/** 0 for a sliver at the carousel's edge, 1 for the focused card. */
@OptIn(ExperimentalMaterial3Api::class)
private val CarouselItemScope.focus: Float
    get() {
        val info = carouselItemDrawInfo
        return if (info.maxSize > info.minSize) {
            ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.HeroCard(
    item: SearchResponse,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .maskClip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        RemoteImage(
            url = item.posterUrl,
            headers = item.posterHeaders,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // The text block reaches well above the bottom edge, so
                    // the scrim has to be opaque where it starts.
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        0.62f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f),
                    )
                )
        )
        // Text belongs to the focused card only; slivers stay pure artwork.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
                .graphicsLayer { alpha = focus * focus },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            typeLabel(item.type)?.let { label ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HomeMetaLine(item)
        }
    }
}

@Composable
private fun HomeMetaLine(item: SearchResponse) {
    val parts = listOfNotNull(item.quality?.name)
    val score = item.score?.toFloat(10)?.takeIf { it > 0f }
    if (score == null && parts.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (score != null) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = " %.1f".format(score),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            if (parts.isNotEmpty()) {
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun typeLabel(type: TvType?): String? = when (type) {
    TvType.Movie, TvType.AnimeMovie -> stringResource(R.string.type_movie)
    TvType.TvSeries, TvType.AsianDrama -> stringResource(R.string.type_series)
    TvType.Anime, TvType.OVA -> stringResource(R.string.type_anime)
    TvType.Cartoon -> stringResource(R.string.type_cartoon)
    null -> null
    else -> type.name
}

/** The same labels for the cinematic hero's [com.kodastream.app.ui.home.hero.HeroItem.type]. */
@Composable
internal fun typeLabel(type: String): String = when (type.trim().lowercase()) {
    "movie" -> stringResource(R.string.type_movie)
    "series" -> stringResource(R.string.type_series)
    else -> type.replaceFirstChar(Char::uppercase)
}
