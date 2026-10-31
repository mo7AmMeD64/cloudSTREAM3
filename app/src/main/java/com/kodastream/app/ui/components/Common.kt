package com.kodastream.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kodastream.app.R
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.launch

/**
 * An image from a provider. Some sites only serve posters when the request
 * carries their own headers (referer, cookies), so those are forwarded.
 * The placeholder is a flat tonal painter, not a subcomposition or a second
 * animation: in a lazy list that keeps each poster to one drawable swap.
 */
@Composable
fun RemoteImage(
    url: String?,
    headers: Map<String, String>?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val context = LocalContext.current
    val placeholder = remember(containerColor) { ColorPainter(containerColor) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            PosterPlaceholder()
        } else {
            val request = remember(url, headers, context) {
                ImageRequest.Builder(context)
                    .data(url)
                    .apply { headers?.forEach { (key, value) -> addHeader(key, value) } }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                placeholder = placeholder,
                error = placeholder,
                onSuccess = { failed = false },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) PosterPlaceholder()
        }
    }
}

/** The tonal film icon shown when a poster is missing or fails to load. */
@Composable
private fun PosterPlaceholder() {
    Icon(
        imageVector = Icons.Rounded.Movie,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(32.dp),
    )
}

/** A poster with its title underneath, the basic unit of every list in the app. */
@Composable
fun PosterCard(
    item: SearchResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    cardWidth: Dp? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    PosterCard(
        name = item.name,
        posterUrl = item.posterUrl,
        posterHeaders = item.posterHeaders,
        score10 = item.score?.toFloat(10),
        onClick = onClick,
        modifier = modifier,
        horizontal = horizontal,
        cardWidth = cardWidth,
        onLongClick = onLongClick,
        selected = selected,
        selectionMode = selectionMode,
    )
}

/** The card every list shares: a tonal surface, a flat placeholder while
 *  the poster loads, a scrim rating badge that stays readable on any image,
 *  and a spring press that reads as physically pressing the card down. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    name: String,
    posterUrl: String?,
    posterHeaders: Map<String, String>?,
    score10: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    cardWidth: Dp? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val ratio = if (horizontal) 16f / 9f else 2f / 3f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = bouncySpring(),
        label = "cardPress",
    )
    var truncated by remember(name) { mutableStateOf(false) }
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (selectionMode) {
                    Modifier.semantics { this.selected = selected }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick ?: if (truncated) {
                    { scope.launch { tooltipState.show() } }
                } else {
                    null
                },
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(text = name, style = MaterialTheme.typography.bodyMedium)
                }
            },
            state = tooltipState,
            enableUserInput = false,
        ) {
            Column {
                Box {
                    RemoteImage(
                        url = posterUrl,
                        headers = posterHeaders,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio),
                    )
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                        )
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    // The badge sits on a photograph, so it takes its contrast
                    // from a scrim, never from the theme: a thumbnail is the same
                    // image in every palette and a theme-derived container is
                    // unreadable in one of the two modes.
                    if (score10 != null && score10 > 0f) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "%.1f".format(score10),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
                PosterTitle(
                    text = name,
                    onTruncatedChange = { truncated = it },
                )
            }
        }
    }
}

/**
 * The one title slot every poster card shares: exactly two lines tall, so
 * every card in a row is the same height, top-aligned and cut with an
 * ellipsis. Direction comes from the layout, not from the first letter of
 * the title, so Arabic and English cards share an edge in either locale.
 */
@Composable
fun PosterTitle(
    text: String,
    modifier: Modifier = Modifier,
    onTruncatedChange: ((Boolean) -> Unit)? = null,
) {
    val textDirection =
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextDirection.Rtl
        else TextDirection.Ltr
    Text(
        text = bidiSafe(text),
        style = MaterialTheme.typography.labelMedium.copy(textDirection = textDirection),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> onTruncatedChange?.invoke(layout.hasVisualOverflow) },
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 10.dp)
            // The card click merges this node, so TalkBack reads the whole
            // title from the description instead of the two visible lines.
            .clearAndSetSemantics { contentDescription = text },
    )
}


@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(44.dp))
    }
}

@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.retry),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text(retryLabel) }
        }
    }
}

/** Empty state: a short title and a hint about what to do next. */
@Composable
fun EmptyBox(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (content != null) {
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = if (onViewMore != null) 8.dp else 16.dp,
                top = 16.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onViewMore != null) {
            TextButton(
                onClick = onViewMore,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.view_more), maxLines = 1)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
