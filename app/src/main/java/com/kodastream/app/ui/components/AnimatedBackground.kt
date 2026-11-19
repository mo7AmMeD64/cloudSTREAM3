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
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val DUST_COUNT = 24
private const val DRIFT_MS = 60_000

/**
 * One particle's fixed patch of screen plus its slow, closed drift. The
 * frequencies are whole cycles per [DRIFT_MS] so the seamless loop never jumps.
 */
private data class Dust(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val driftXDp: Float,
    val driftYDp: Float,
    val phase: Float,
    val cycles: Int,
    val alpha: Float,
)

/**
 * The app's page background: the theme colour with a few slow, faint dust
 * particles over it. It is decorative, draws no touch targets, and belongs
 * first in a Box so every screen and navigation surface stays above it.
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dustColor = MaterialTheme.colorScheme.onBackground
    val dust = remember {
        val random = Random(0x5EED)
        List(DUST_COUNT) {
            Dust(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radiusDp = 0.5f + random.nextFloat() * 0.9f,
                driftXDp = 8f + random.nextFloat() * 14f,
                driftYDp = 10f + random.nextFloat() * 22f,
                phase = random.nextFloat() * 2f * PI.toFloat(),
                cycles = if (random.nextFloat() < 0.5f) 1 else 2,
                alpha = 0.035f + random.nextFloat() * 0.075f,
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
            val angle = drift * particle.cycles + particle.phase
            val x = particle.x * size.width + sin(angle) * particle.driftXDp.dp.toPx()
            val y = particle.y * size.height + sin(angle + 1.7f) * particle.driftYDp.dp.toPx()
            val twinkle = 0.7f + 0.3f * sin(angle * 2f + particle.phase)
            drawCircle(
                color = dustColor,
                radius = particle.radiusDp.dp.toPx(),
                center = Offset(x, y),
                alpha = particle.alpha * twinkle,
            )
        }
    }
}
