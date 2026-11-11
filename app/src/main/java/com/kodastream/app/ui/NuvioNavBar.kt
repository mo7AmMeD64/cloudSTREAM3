package com.kodastream.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodastream.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * NuvioMobile's own bar colours, not Material roles, so the style matches
 * Nuvio's default WHITE theme exactly: #1C1C1E glass, white accent, muted
 * #969CA3 labels. The fallback surface is Nuvio's non-haze one (0.82 alpha)
 * plus its 0.75dp white edge.
 */
private val NuvioBarSurface = Color(0xFF1C1C1E)
private val NuvioBarAccent = Color(0xFFF5F5F5)
private val NuvioBarMuted = Color(0xFF969CA3)
private val NuvioBarFont = FontFamily(
    Font(R.font.jetbrains_sans_regular, FontWeight.Normal),
    Font(R.font.jetbrains_sans_bold, FontWeight.Bold),
)

/**
 * The Nuvio navigation bar, ported from NuvioMobile's jelly bar: a floating
 * pill whose selection slides between tabs on a critically damped spring,
 * stretches under a drag, and rubber-bands vertically. Tabs can be tapped or
 * dragged; the labels collapse while the content scrolls down and return on
 * the first upward scroll.
 *
 * The shell picks this bar or the existing Material floating pill; neither
 * style changes the destinations or how they navigate.
 */
@Composable
internal fun NuvioNavBar(
    destinations: List<TopLevel>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    scrollState: NuvioNavBarScrollState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    maxWidth: Dp = 400.dp,
    expandedHeight: Dp = 64.dp,
) {
    if (destinations.isEmpty()) return
    val items = destinations.map { destination ->
        NuvioNavItem(
            label = stringResource(destination.labelRes),
            selected = destination.route == selectedRoute,
            onClick = { onSelect(destination.route) },
            icon = destination.nuvioIcon,
            iconRes = destination.nuvioIconRes,
        )
    }
    val accent = NuvioBarAccent
    val muted = NuvioBarMuted
    val labelFraction by animateFloatAsState(
        targetValue = scrollState.labelVisibility,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "nuvio_nav_labels",
    )
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val selectedIndex = items.indexOfFirst { it.selected }
    val visualSelectedIndex = visualNavIndex(selectedIndex, items.size, isRtl)
    val motion = remember(items.size, isRtl) { JellyMotion(visualSelectedIndex, items.size) }
    val currentItems by rememberUpdatedState(items)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val density = LocalDensity.current
    val scale = iconSize.value / 28f
    val labelHeight = 16.dp * scale
    // Keep the content from clipping when the icon is enlarged but the
    // height slider is small.
    val trackHeight =
        (expandedHeight.coerceAtLeast(iconSize + labelHeight + 8.dp) - labelHeight) +
            labelHeight * labelFraction
    val horizontalPadding = 28.dp + (58.dp - 28.dp) * (1f - labelFraction)

    LaunchedEffect(visualSelectedIndex, items.size) {
        motion.select(visualSelectedIndex)
    }
    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    motion.resize(it.width / density.density, it.height / density.density, items.size)
                }
                .pointerInput(motion, density, items.size, isRtl) {
                    detectJellyTabGestures(motion, density.density, { currentItems }, { currentIsRtl })
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = motion.frame.trackScale
                        scaleY = motion.frame.trackScale
                    },
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val frame = motion.frame
                            transformOrigin = TransformOrigin(
                                if (size.width > 0) frame.originX * density.density / size.width else 0.5f,
                                0.5f,
                            )
                            scaleX = frame.trackScaleX
                            translationY = frame.trackOffsetY * density.density
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { translationX = motion.frame.panelOffset * density.density },
                    ) {
                        Box(
                            Modifier.matchParentSize()
                                .clip(RoundedCornerShape(50))
                                .drawWithContent {
                                    drawContent()
                                    drawJellyGlow(motion.frame, accent)
                                },
                        ) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .drawWithCache {
                                        val fill = NuvioBarSurface.copy(alpha = 0.82f)
                                        val edge = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.27f),
                                                Color.White.copy(alpha = 0.02f),
                                            )
                                        )
                                        val width = 0.75.dp.toPx()
                                        onDrawBehind {
                                            drawRect(fill)
                                            drawRoundRect(
                                                brush = edge,
                                                topLeft = Offset(width / 2, width / 2),
                                                size = Size(size.width - width, size.height - width),
                                                cornerRadius = CornerRadius((size.height - width) / 2),
                                                style = Stroke(width),
                                            )
                                        }
                                    },
                            )
                        }
                        Box(
                            Modifier.matchParentSize().drawWithContent {
                                if (selectedIndex >= 0) {
                                    clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    drawContent()
                                }
                            },
                        ) {
                            JellyTabRow(
                                items = items,
                                labelFraction = labelFraction,
                                motion = motion,
                                active = false,
                                accent = accent,
                                muted = muted,
                                scale = scale,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        if (selectedIndex >= 0) {
                            Box(
                                Modifier.matchParentSize()
                                    .clearAndSetSemantics {}
                                    .drawWithContent {
                                        drawJellyPill(
                                            motion.frame,
                                            items.size,
                                            accent.copy(alpha = 0.15f),
                                            accent,
                                        ) { drawContent() }
                                    },
                            ) {
                                JellyTabRow(
                                    items = items,
                                    labelFraction = labelFraction,
                                    motion = motion,
                                    active = true,
                                    accent = accent,
                                    muted = muted,
                                    scale = scale,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                        JellyTabTargets(
                            items = items,
                            motion = motion,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
}

private class NuvioNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val iconRes: Int,
)

/** 1f = labels fully visible, 0f = icons only. */
@Stable
internal class NuvioNavBarScrollState {
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero
        }
    }

    private companion object {
        const val SCROLL_THRESHOLD = 60f
    }
}

@Composable
internal fun rememberNuvioNavBarScrollState(): NuvioNavBarScrollState =
    remember { NuvioNavBarScrollState() }

@Composable
private fun JellyTabRow(
    items: List<NuvioNavItem>,
    labelFraction: Float,
    motion: JellyMotion,
    active: Boolean,
    accent: Color,
    muted: Color,
    scale: Float,
    modifier: Modifier,
) {
    val color = if (active) accent else muted
    val iconSize = 28.dp * scale
    val labelHeight = 16.dp * scale
    val iconTint = if (active) accent else muted
    Row(
        modifier = modifier.padding(4.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(
                Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                    val contentScale = if (active) motion.frame.contentScale else 1f
                    scaleX = contentScale
                    scaleY = contentScale
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(iconSize).graphicsLayer {
                            translationY = 2.dp.toPx() * scale * labelFraction
                        }
                    ) {
                        if (item.iconRes != 0) {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                                tint = iconTint,
                            )
                        } else {
                            Icon(item.icon, null, Modifier.size(iconSize), tint = iconTint)
                        }
                    }
                    Box(
                        Modifier.height(labelHeight * labelFraction)
                            .fillMaxWidth()
                            .clipToBounds()
                            .alpha(labelFraction)
                    ) {
                        Text(
                            text = item.label,
                            color = color,
                            style = TextStyle(
                                fontFamily = NuvioBarFont,
                                fontSize = 13.sp * scale,
                                lineHeight = 16.sp * scale,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyTabTargets(
    items: List<NuvioNavItem>,
    motion: JellyMotion,
    modifier: Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier.padding(horizontal = 4.dp).selectableGroup()) {
        items.forEachIndexed { index, item ->
            val visualIndex = visualNavIndex(index, items.size, isRtl)
            val onClick = {
                motion.select(visualIndex)
                item.onClick()
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .selectable(
                        selected = item.selected,
                        role = Role.Tab,
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = item.selected
                        contentDescription = item.label
                        onClick { onClick(); true }
                    },
            )
        }
    }
}

internal fun visualNavIndex(logicalIndex: Int, count: Int, isRtl: Boolean): Int =
    if (logicalIndex in 0 until count && isRtl) count - 1 - logicalIndex else logicalIndex

internal fun logicalNavIndex(visualIndex: Int, count: Int, isRtl: Boolean): Int =
    if (visualIndex in 0 until count && isRtl) count - 1 - visualIndex else visualIndex

private suspend fun PointerInputScope.detectJellyTabGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<NuvioNavItem>,
    isRtl: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.begin(down.position.x / density, down.position.y / density)
        var claimed = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                val delta = change.position - down.position
                if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                if (claimed) change.consume()
                awaitPointerEvent(PointerEventPass.Main)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                if (change.isConsumed && !claimed) break
                motion.drag(delta.x / density, delta.y / density)
                if (!change.pressed) {
                    val visualIndex = motion.finish()
                    val items = currentItems()
                    val logicalIndex = logicalNavIndex(visualIndex, items.size, isRtl())
                    finished = true
                    items.getOrNull(logicalIndex)?.onClick?.invoke()
                    change.consume()
                    break
                }
            }
        } finally {
            if (!finished) {
                val items = currentItems()
                val selectedIndex = items.indexOfFirst { it.selected }
                motion.cancel(visualNavIndex(selectedIndex, items.size, isRtl()))
            }
        }
    }
}

// ---- Jelly physics and drawing, ported from NuvioMobile ----

internal data class JellyFrame(
    val position: Float,
    val pillScaleX: Float = 1f,
    val pillScaleY: Float = 1f,
    val contentScale: Float = 1f,
    val panelOffset: Float = 0f,
    val trackScale: Float = 1f,
    val trackScaleX: Float = 1f,
    val trackOffsetY: Float = 0f,
    val originX: Float = 0f,
    val glowY: Float = 0f,
    val glowOpacity: Float = 0f,
)

@Stable
internal class JellyMotion(initialIndex: Int, count: Int) {
    private val position = JellySpring(initialIndex.coerceAtLeast(0).toDouble(), 1000.0, 1.0)
    private val velocity = JellySpring(0.0, 300.0, 0.5)
    private val press = JellySpring(0.0, 1000.0, 1.0)
    private val scaleX = JellySpring(1.0, 250.0, 0.6)
    private val scaleY = JellySpring(1.0, 250.0, 0.7)
    private val panel = JellySpring(0.0, 300.0, 1.0)
    private val distortionDamping = 18.0 / (2 * sqrt(240.0 * 0.9))
    private val trackY = JellySpring(0.0, 240.0 / 0.9, distortionDamping)
    private val trackX = JellySpring(1.0, 240.0 / 0.9, distortionDamping)
    private val trackPress = JellySpring(1.0, 240.0 / 0.9, distortionDamping)
    private val glow = JellySpring(0.0, 240.0 / 0.9, distortionDamping)
    private var target = position.value
    private var pressTarget = 0.0
    private var shapeTarget = 1.0
    private var releasePending = false
    private var downX = 0.0
    private var downY = 0.0
    private var dragStartTarget = target
    private var dragStartPanel = 0.0
    private var dragStartY = 0.0
    private var movedDistance = 0.0
    private var originX = 0.0
    private var width = 0.0
    private var height = 64.0
    private var tabCount = count
    private val maxIndex get() = (tabCount - 1).coerceAtLeast(0)
    private val tabWidth get() = ((width - 8) / tabCount.coerceAtLeast(1)).coerceAtLeast(0.0)

    var dragging = false
        private set
    var running by mutableStateOf(false)
        private set
    var frame by mutableStateOf(JellyFrame(position.value.toFloat()))
        private set

    fun resize(width: Float, height: Float, count: Int) {
        this.width = width.toDouble()
        this.height = height.toDouble()
        tabCount = count
        target = target.coerceIn(0.0, maxIndex.toDouble())
        if (!dragging) originX = this.width / 2
        publish()
    }

    fun select(index: Int) {
        dragging = false
        if (index >= 0) target = index.coerceAtMost(maxIndex).toDouble()
        releasePending = true
        pressTarget = 0.0
        shapeTarget = 1.0
        running = true
    }

    fun begin(x: Float, y: Float) {
        downX = x.toDouble()
        downY = y.toDouble().coerceIn(0.0, height)
        originX = downX.coerceIn(0.0, width)
        dragStartY = trackY.value
        movedDistance = 0.0
        if (tabWidth > 0) target = indexAt(downX).toDouble()
        dragStartTarget = target
        dragStartPanel = panel.value
        dragging = true
        releasePending = false
        pressTarget = 1.0
        shapeTarget = 1.3
        panel.velocity = 0.0
        running = true
    }

    fun drag(x: Float, y: Float) {
        if (!dragging || tabWidth <= 0) return
        val dx = x.toDouble()
        val dy = y.toDouble()
        target = (dragStartTarget + dx / tabWidth).coerceIn(0.0, maxIndex.toDouble())
        panel.snapTo(dragStartPanel + dx)
        trackY.snapTo(dragStartY + jellyRubberBand(dy, height) * 0.25)
        trackX.snapTo(1 - (abs(dy) / 700).coerceAtMost(1.0) * 0.08)
        originX = (downX + dx).coerceIn(0.0, width)
        movedDistance = max(movedDistance, max(abs(dx), abs(dy)))
        publish()
    }

    fun finish(): Int {
        val index = if (movedDistance < 4 && tabWidth > 0) indexAt(downX)
        else floor(target + 0.5).toInt().coerceIn(0, maxIndex)
        dragging = false
        panel.velocity = 0.0
        target = index.toDouble()
        releasePending = true
        running = true
        return index
    }

    fun cancel(selectedIndex: Int) {
        dragging = false
        panel.velocity = 0.0
        select(selectedIndex)
    }

    fun advance(seconds: Double) {
        val delta = seconds.coerceIn(0.0, 0.064)
        target = target.coerceIn(0.0, maxIndex.toDouble())
        position.advance(target, delta)
        velocity.advance(if (dragging && maxIndex > 0) position.velocity / maxIndex else 0.0, delta)
        if (!dragging) panel.advance(0.0, delta)
        if (releasePending && abs(position.value - target) < max(1, maxIndex) * 0.025) {
            releasePending = false
            pressTarget = 0.0
            shapeTarget = 1.0
        }
        press.advance(pressTarget, delta)
        scaleX.advance(shapeTarget, delta)
        scaleY.advance(shapeTarget, delta)
        trackPress.advance(if (dragging) 1.025 else 1.0, delta)
        glow.advance(if (dragging) 1.0 else 0.0, delta)
        if (!dragging) {
            trackY.advance(0.0, delta)
            trackX.advance(1.0, delta)
            if (trackX.isAtRest(1.0)) originX = width / 2
        }
        publish()
        running = dragging || releasePending || !position.isAtRest(target) || !velocity.isAtRest(0.0) ||
            !press.isAtRest(0.0) || !scaleX.isAtRest(1.0) || !scaleY.isAtRest(1.0) ||
            !panel.isAtRest(0.0) || !trackY.isAtRest(0.0) || !trackX.isAtRest(1.0) ||
            !trackPress.isAtRest(1.0) || !glow.isAtRest(0.0)
    }

    private fun indexAt(x: Double): Int = floor((x - 4) / tabWidth).toInt().coerceIn(0, maxIndex)

    private fun publish() {
        val speed = velocity.value / 10
        frame = JellyFrame(
            position = position.value.toFloat(),
            pillScaleX = (scaleX.value / (1 - (speed * 0.75).coerceIn(-0.2, 0.2))).toFloat(),
            pillScaleY = (scaleY.value * (1 - (speed * 0.25).coerceIn(-0.2, 0.2))).toFloat(),
            contentScale = (1 + 0.2 * press.value).toFloat(),
            panelOffset = jellyPanelOffset(panel.value, width),
            trackScale = trackPress.value.toFloat(),
            trackScaleX = trackX.value.toFloat(),
            trackOffsetY = trackY.value.toFloat(),
            originX = originX.toFloat(),
            glowY = downY.toFloat(),
            glowOpacity = glow.value.toFloat().coerceIn(0f, 1f),
        )
    }
}

internal class JellySpring(
    var value: Double,
    private val stiffness: Double,
    private val dampingRatio: Double,
) {
    var velocity = 0.0

    fun isAtRest(target: Double): Boolean = abs(value - target) < 0.0001 && abs(velocity) < 0.0001

    fun snapTo(target: Double) {
        value = target
        velocity = 0.0
    }

    fun advance(target: Double, seconds: Double) {
        if (isAtRest(target)) {
            snapTo(target)
            return
        }
        val displacement = value - target
        val frequency = sqrt(stiffness)
        if (dampingRatio == 1.0) {
            val decay = exp(-frequency * seconds)
            val coefficient = velocity + frequency * displacement
            value = target + (displacement + coefficient * seconds) * decay
            velocity = (velocity - frequency * coefficient * seconds) * decay
        } else {
            val damping = dampingRatio * frequency
            val damped = frequency * sqrt(1 - dampingRatio * dampingRatio)
            val decay = exp(-damping * seconds)
            val cosine = cos(damped * seconds)
            val sine = sin(damped * seconds)
            val positionCoefficient = (velocity + damping * displacement) / damped
            val velocityCoefficient = (damping * velocity + stiffness * displacement) / damped
            value = target + decay * (displacement * cosine + positionCoefficient * sine)
            velocity = decay * (velocity * cosine - velocityCoefficient * sine)
        }
    }
}

internal fun jellyRubberBand(distance: Double, dimension: Double): Double {
    if (distance == 0.0 || dimension <= 0.0) return 0.0
    val damped = (1 - 1 / (abs(distance) * 0.14 / dimension + 1)) * dimension
    return if (distance < 0) -damped else damped
}

internal fun jellyPanelOffset(rawOffset: Double, width: Double): Float {
    if (width <= 0 || rawOffset == 0.0) return 0f
    val fraction = (rawOffset / width).coerceIn(-1.0, 1.0)
    val x = abs(fraction)
    var low = 0.0
    var high = 1.0
    var parameter = x
    repeat(10) {
        val bezierX = parameter * parameter * (3 * (1 - parameter) * 0.58 + parameter)
        if (bezierX < x) low = parameter else high = parameter
        parameter = (low + high) / 2
    }
    val eased = parameter * parameter * (3 * (1 - parameter) + parameter)
    return ((if (fraction < 0) -4 else 4) * eased).toFloat()
}

private fun DrawScope.drawJellyGlow(frame: JellyFrame, color: Color) {
    if (frame.glowOpacity <= 0f || color.alpha <= 0f) return
    val alpha = 0.15f * frame.glowOpacity * color.alpha
    drawRect(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.45f to color.copy(alpha = alpha * 0.43f),
            1f to color.copy(alpha = 0f),
            center = Offset(frame.originX.dp.toPx(), frame.glowY.dp.toPx()),
            radius = 300.dp.toPx(),
        ),
        topLeft = Offset(-48.dp.toPx(), -16.dp.toPx()),
        size = Size(size.width + 96.dp.toPx(), size.height + 32.dp.toPx()),
    )
}

private fun DrawScope.jellyPillPath(frame: JellyFrame, count: Int): Path {
    val inset = 4.dp.toPx()
    val tabWidth = (size.width - 2 * inset) / count
    val itemHeight = size.height - 2 * inset
    val centerX = inset + (frame.position + 0.5f) * tabWidth
    val halfWidth = tabWidth * frame.pillScaleX / 2
    val halfHeight = itemHeight * frame.pillScaleY / 2
    val radius = min(tabWidth, itemHeight) / 2
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = centerX - halfWidth,
                top = size.height / 2 - halfHeight,
                right = centerX + halfWidth,
                bottom = size.height / 2 + halfHeight,
                cornerRadius = CornerRadius(radius * frame.pillScaleX, radius * frame.pillScaleY),
            ),
        )
    }
}

private fun DrawScope.drawJellyPill(
    frame: JellyFrame,
    count: Int,
    surfaceColor: Color,
    glowColor: Color,
    content: () -> Unit,
) {
    clipPath(jellyPillPath(frame, count)) {
        drawRect(
            color = surfaceColor,
            topLeft = Offset(-48.dp.toPx(), -16.dp.toPx()),
            size = Size(size.width + 96.dp.toPx(), size.height + 32.dp.toPx()),
        )
        drawJellyGlow(frame, glowColor)
        content()
    }
}
