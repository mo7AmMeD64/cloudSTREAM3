package com.kodastream.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders that stand in for content at its real size and shape,
 * so nothing reflows when the data lands. The screen's chrome never waits and
 * is never replaced by a spinner - only the regions actually blocked on data.
 *
 * One pulse per screen, read in the draw phase: a placeholder that read its
 * own animated alpha in composition would repaint and recompose the whole
 * loading screen every frame.
 */
private const val SKELETON_PULSE_MS = 900

private val LocalSkeletonPulse = staticCompositionLocalOf<State<Float>> {
    mutableStateOf(0.4f)
}

/** One shared breathing alpha for every placeholder on the screen. */
@Composable
fun SkeletonPulseProvider(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val pulse = transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    CompositionLocalProvider(LocalSkeletonPulse provides pulse, content = content)
}

/**
 * A single placeholder block. Size it with [modifier] to match whatever it is
 * standing in for; [shape] should match too, so the swap to real content is a
 * fill rather than a reflow. Pass null for edge-to-edge artwork.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape? = MaterialTheme.shapes.medium,
) {
    val pulse = LocalSkeletonPulse.current
    // surfaceContainerHighest is close to the tinted light page at these
    // alphas; the outline tone keeps the placeholder visible there. Dark is
    // unchanged.
    val color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .drawBehind { drawRect(color.copy(alpha = pulse.value)) }
            .clearAndSetSemantics {},
    )
}

/** Placeholder for a line of text, sized like the type it replaces. */
@Composable
fun SkeletonTextLine(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    SkeletonBox(
        modifier = modifier.size(width = width, height = height),
        shape = MaterialTheme.shapes.small,
    )
}

/**
 * Stand-in for a [PosterCard]: the image at the card's real aspect and width,
 * then two title lines on the card body. Mirrors the real card's shape and
 * padding so the swap does not reflow.
 */
@Composable
fun PosterCardSkeleton(
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val width = if (horizontal) 220.dp else 128.dp
    val ratio = if (horizontal) 16f / 9f else 2f / 3f
    Surface(
        modifier = modifier.width(width),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(14.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp))
            }
        }
    }
}

/**
 * A run of poster placeholders standing in for a shelf row, one screen-wide
 * `LazyRow` item while the real row is loading.
 */
@Composable
fun PosterShelfSkeleton(
    count: Int = 4,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            PosterCardSkeleton(horizontal = horizontal)
        }
    }
}

/**
 * Stand-in for an episode row: a 120dp-wide 16:9 thumbnail beside a title and
 * a metadata line. Also the right shape for any thumbnail-plus-two-lines row.
 */
@Composable
fun ThumbRowSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.small,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonTextLine(width = 150.dp, height = 14.dp)
                SkeletonTextLine(width = 80.dp, height = 11.dp)
            }
        }
    }
}
