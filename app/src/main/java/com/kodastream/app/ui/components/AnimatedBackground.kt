package com.kodastream.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val DUST_COUNT = 40
private const val DRIFT_MS = 60_000

/**
 * One particle's fixed patch of screen plus its slow, closed drift. Whole-cycle
 * frequencies keep the endless loop seamless, and 1:2 axes give it a little
 * figure-eight travel instead of a flat bob.
 */
private data class Dust(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val glowScale: Float,
    val driftXDp: Float,
    val driftYDp: Float,
    val phase: Float,
    val cyclesX: Int,
    val cyclesY: Int,
    val alpha: Float,
    val tint: Int,
)

/**
 * The app's page background: the theme colour with slow, glowing dust over it.
 * A soft halo under each dot keeps the effect readable in both light and dark
 * themes without becoming a bright overlay. Decorative only: it draws no touch
 * targets, and belongs first in a Box so every screen stays above it.
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dustColor = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.tertiary
    val dust = remember {
        val random = Random(0x5EED)
        List(DUST_COUNT) {
            val cyclesX = if (random.nextFloat() < 0.5f) 1 else 2
            Dust(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radiusDp = 0.9f + random.nextFloat() * 1.5f,
                glowScale = 2.8f + random.nextFloat() * 1.7f,
                driftXDp = 16f + random.nextFloat() * 36f,
                driftYDp = 20f + random.nextFloat() * 44f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
                cyclesX = cyclesX,
                cyclesY = if (cyclesX == 1) 2 else 1,
                alpha = 0.16f + random.nextFloat() * 0.22f,
                tint = when {
                    random.nextFloat() < 0.55f -> 0
                    random.nextFloat() < 0.55f -> 1
                    else -> 2
                },
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "ambientDust")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(DRIFT_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientDustDrift",
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        dust.forEach { particle ->
            val xAngle = drift * particle.cyclesX + particle.phase
            val yAngle = drift * particle.cyclesY + particle.phase * 1.3f
            val x = particle.x * size.width + sin(xAngle) * particle.driftXDp.dp.toPx()
            val y = particle.y * size.height + sin(yAngle) * particle.driftYDp.dp.toPx()
            val twinkle = 0.55f + 0.45f * sin(drift * 2f + particle.phase * 2f)
            val color: Color = when (particle.tint) {
                0 -> dustColor
                1 -> accent
                else -> secondaryAccent
            }
            val radius = particle.radiusDp.dp.toPx()
            val alpha = particle.alpha * twinkle
            drawCircle(
                color = color,
                radius = radius * particle.glowScale,
                center = Offset(x, y),
                alpha = alpha * 0.22f,
            )
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(x, y),
                alpha = alpha,
            )
        }
    }
}
