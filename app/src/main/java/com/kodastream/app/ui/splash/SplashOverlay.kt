package com.kodastream.app.ui.splash

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.kodastream.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Launch animation drawn over the app while it builds its first screen.
 *
 * The same storyboard as the approved 3 s preview, drawn with Canvas instead
 * of a video: sharp at any density, and the soundtrack comes from res/raw.
 * Tap skips; sound respects silent/priority ringer modes.
 */
@Composable
fun SplashOverlay(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = remember(context) { SplashPainter(context) }
    var progress by remember { mutableFloatStateOf(0f) }

    // A frame clock, not tween: system "animator duration scale" is ignored,
    // so the intro is 3 s even when the phone shortens UI animations.
    LaunchedEffect(Unit) {
        var start = 0L
        withFrameNanos { start = it }
        var p = 0f
        while (p < 1f) {
            withFrameNanos { now ->
                p = ((now - start) / 1_000_000_000f / SplashTimeline.DISPLAY_SECONDS)
                    .coerceIn(0f, 1f)
            }
            progress = p
        }
        onDone()
    }

    DisposableEffect(Unit) {
        val audio = SplashAudio(context)
        audio.start()
        onDispose { audio.stop() }
    }

    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val state = SplashTimeline.state(SplashTimeline.authoredAt(progress))
                scaleX = state.exitScale
                scaleY = state.exitScale
                alpha = state.exitAlpha
            }
            .pointerInput(Unit) { detectTapGestures { onDone() } }
    ) {
        val state = SplashTimeline.state(SplashTimeline.authoredAt(progress))
        // Cover the whole window (including odd aspect ratios) without bars.
        val scale = max(size.width / DESIGN_W, size.height / DESIGN_H)
        withTransform({
            translate(
                (size.width - DESIGN_W * scale) / 2f,
                (size.height - DESIGN_H * scale) / 2f,
            )
            scale(scale, scale, Offset.Zero)
        }) {
            painter.draw(this, state)
        }
    }
}

private const val DESIGN_W = 720f
private const val DESIGN_H = 1280f
private const val FACE_CX = 360f
private const val FACE_CY = 565f
private const val WORD_TOP = 826f
private const val WORD_INK_TOP = 25f
private const val WORD_BOX_HEIGHT = 147f
private const val WORD_WIDTH = 380f
private const val WORD_GAP = 4f
private const val TAU = 6.2831855f

private val BG_STOPS = arrayOf(
    0.00f to Color(0xFF000010),
    0.22f to Color(0xFF000834),
    0.48f to Color(0xFF011360),
    0.74f to Color(0xFF00229E),
    1.00f to Color(0xFF024EEC),
)

private val WORD_CHARS = charArrayOf('c', 'l', 'o', 'u', 'd')

private val STAR = Path().apply {
    moveTo(100f, 12f)
    cubicTo(105f, 72f, 111f, 91f, 188f, 100f)
    cubicTo(111f, 109f, 105f, 128f, 100f, 188f)
    cubicTo(95f, 128f, 89f, 109f, 12f, 100f)
    cubicTo(89f, 91f, 95f, 72f, 100f, 12f)
    close()
}

private val SMIRK = Path().apply {
    moveTo(322f, 750f)
    cubicTo(405f, 776f, 490f, 762f, 568f, 710f)
    cubicTo(576f, 705f, 583f, 702f, 590f, 701f)
}

private val RIPPLE_STARTS = floatArrayOf(0.70f, 2.24f, 2.33f, 3.98f)
private val RIPPLE_LIVES = floatArrayOf(0.50f, 0.62f, 0.58f, 0.60f)
private val RIPPLE_FROM = floatArrayOf(120f, 210f, 180f, 220f)
private val RIPPLE_TO = floatArrayOf(300f, 520f, 430f, 560f)
private val RIPPLE_ALPHA = floatArrayOf(0.22f, 0.40f, 0.24f, 0.16f)

private val SPARKLE_STARTS = floatArrayOf(2.22f, 2.30f, 2.24f, 2.34f, 2.28f)
private val SPARKLE_X = floatArrayOf(640f, 700f, 92f, 262f, 618f)
private val SPARKLE_Y = floatArrayOf(250f, 360f, 330f, 212f, 470f)
private val SPARKLE_SIZES = floatArrayOf(110f, 72f, 82f, 56f, 50f)

private val TWINKLE_STARTS = floatArrayOf(3.32f, 3.52f, 3.72f)
private val TWINKLE_X = floatArrayOf(148f, 618f, 520f)
private val TWINKLE_Y = floatArrayOf(300f, 392f, 980f)
private val TWINKLE_SIZES = floatArrayOf(44f, 52f, 38f)

private val SWEEP_STARTS = floatArrayOf(1.10f, 3.28f)

private fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun easeInOut(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    if (c < 0.5f) return 4f * c * c * c
    val u = 2f - 2f * c
    return 1f - u * u * u / 2f
}

private class Particle(
    val x: Float,
    val startY: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
)

private class SplashPainter(context: Context) {

    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterX = FloatArray(6)
    private val baseline: Float

    private val smile = Path().apply {
        moveTo(134f, 720f)
        cubicTo(268f, 833f, 450f, 836f, 568f, 712f)
        cubicTo(587f, 692f, 601f, 682f, 614f, 678f)
    }
    private val smileMeasure = PathMeasure().apply { setPath(smile, false) }
    private val smileSegment = Path()

    private val particles: List<Particle>

    init {
        wordPaint.typeface = ResourcesCompat.getFont(context, R.font.productsans_bold)
        wordPaint.textSize = 104f
        wordPaint.style = Paint.Style.FILL
        wordPaint.textScaleX = 1f
        // The preview was scaled to 380 px wide; textScaleX does that horizontally.
        val unit = wordPaint.measureText("cloud") + WORD_GAP + wordPaint.measureText("3")
        wordPaint.textScaleX = WORD_WIDTH / unit
        letterX[0] = 0f
        letterX[1] = wordPaint.measureText("c")
        letterX[2] = wordPaint.measureText("cl")
        letterX[3] = wordPaint.measureText("clo")
        letterX[4] = wordPaint.measureText("clou")
        letterX[5] = wordPaint.measureText("cloud") + WORD_GAP * wordPaint.textScaleX
        val bounds = Rect()
        wordPaint.getTextBounds("cloud", 0, 5, bounds)
        baseline = WORD_TOP + WORD_INK_TOP - bounds.top

        val random = Random(11)
        particles = List(26) {
            Particle(
                x = 30f + random.nextFloat() * (DESIGN_W - 60f),
                startY = random.nextFloat() * DESIGN_H,
                radius = 1f + random.nextFloat() * 1.4f,
                speed = 10f + random.nextFloat() * 16f,
                phase = random.nextFloat() * TAU,
            )
        }
    }

    fun draw(scope: DrawScope, state: SplashState) = with(scope) {
        drawBackground(state.time)
        drawAmbient(state)
        drawFace(state)
        drawRipples(state.time)
        drawSparkles(state.time)
        drawFlash(state.time)
        drawSweeps(state.time)
        drawWordmark(state)
    }

    private fun DrawScope.drawBackground(t: Float) {
        drawRect(Brush.verticalGradient(*BG_STOPS), Offset.Zero, Size(DESIGN_W, DESIGN_H))

        // Slow pan on the bloom keeps the plate alive under the face.
        val panX = 10f * sin(TAU * 0.09f * t + 0.5f)
        val panY = 14f * sin(TAU * 0.07f * t)
        withTransform({ scale(1f, 1.35f, Offset(360f, 512f)) }) {
            val center = Offset(360f + panX, 512f + panY)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0x590C1A60),
                    0.5f to Color(0x2E0C1A60),
                    1f to Color.Transparent,
                    center = center,
                    radius = 480f,
                ),
                radius = 480f,
                center = center,
            )
        }

        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.42f),
                center = Offset(360f, 640f),
                radius = 780f,
            ),
            Offset.Zero,
            Size(DESIGN_W, DESIGN_H),
        )
    }

    private fun DrawScope.drawAmbient(state: SplashState) {
        val t = state.time
        drawBlob(
            cx = 360f + 130f * sin(0.42f * t + 1.1f),
            cy = 470f + 80f * cos(0.35f * t),
            radius = 380f,
            color = Color(0xFF3CC8FF),
            peak = 0.30f,
        )
        drawBlob(
            cx = 360f - 150f * sin(0.37f * t + 0.2f),
            cy = 780f + 70f * sin(0.31f * t + 2f),
            radius = 350f,
            color = Color(0xFF6C5CFF),
            peak = 0.20f,
        )
        val glowScale = 1f + 0.05f * sin(TAU * 0.26f * t)
        val glowAlpha = 0.34f + 0.07f * sin(TAU * 0.33f * t + 0.6f)
        drawBlob(360f, 540f, 460f * glowScale, Color(0xFF4280FF), 0.75f * glowAlpha)
        drawParticles(t)
    }

    private fun DrawScope.drawBlob(cx: Float, cy: Float, radius: Float, color: Color, peak: Float) {
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = peak),
                0.45f to color.copy(alpha = peak * 0.5f),
                0.75f to color.copy(alpha = peak * 0.12f),
                1f to Color.Transparent,
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    private fun DrawScope.drawParticles(t: Float) {
        val fade = (t / 0.5f).coerceIn(0f, 1f)
        if (fade <= 0f) return
        val span = DESIGN_H + 80f
        for (p in particles) {
            var y = (p.startY - p.speed * t) % span
            if (y < 0f) y += span
            y -= 40f
            val x = p.x + 10f * sin(p.phase + t * 0.9f)
            val alpha = (0.10f + 0.13f * (0.5f + 0.5f * sin(p.phase + t * 1.3f))) * fade
            drawCircle(Color.White.copy(alpha = alpha), p.radius, Offset(x, y))
        }
    }

    private fun DrawScope.drawFace(state: SplashState) {
        withTransform({
            translate(0f, state.bob)
            rotate(state.tilt, Offset(FACE_CX, FACE_CY))
            scale(state.scale, state.scale, Offset(FACE_CX, FACE_CY))
        }) {
            drawEye(255f, 482f, 168f, 230f, state.eyeAlphaLeft, state.eyeDropLeft, state)
            drawEye(468f, 466f, 198f, 272f, state.eyeAlphaRight, state.eyeDropRight, state)
            drawSmile(state)
            drawSmirk(state)
            drawMouth(state)
            drawMarks(state)
        }
    }

    private fun DrawScope.drawEye(
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        alpha: Float,
        drop: Float,
        state: SplashState,
    ) {
        if (alpha <= 0.003f) return
        // Sprite is 540x720 holding a 300x400 oval; only the oval is painted.
        val w = width * state.eyeScale * (300f / 540f)
        val h = height * state.eyeScale * state.blink * (400f / 720f)
        if (w < 1f || h < 1f) return
        val center = Offset(cx + state.look, cy + drop + state.breath)
        val top = center.y - h / 2f
        val keep = h * (1f - 0.5f * state.squint)
        // Squint keeps the bottom of the eye, so clip from the top down.
        clipRect(
            left = -200f,
            top = if (state.squint > 0.001f) top + h - keep else -200f,
            right = DESIGN_W + 200f,
            bottom = DESIGN_H + 200f,
        ) {
            val topLeft = Offset(center.x - w / 2f, top)
            drawOval(
                Color.White.copy(alpha = 0.02f * alpha),
                Offset(topLeft.x - 18f, topLeft.y - 18f),
                Size(w + 36f, h + 36f),
            )
            drawOval(
                Color.White.copy(alpha = 0.035f * alpha),
                Offset(topLeft.x - 10f, topLeft.y - 10f),
                Size(w + 20f, h + 20f),
            )
            drawOval(
                Color.White.copy(alpha = 0.05f * alpha),
                Offset(topLeft.x - 4f, topLeft.y - 4f),
                Size(w + 8f, h + 8f),
            )
            drawOval(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = alpha),
                    0.45f to Color.White.copy(alpha = alpha),
                    1f to Color(0xFFCBDEFF).copy(alpha = alpha),
                    startY = top,
                    endY = top + h,
                ),
                topLeft = topLeft,
                size = Size(w, h),
            )
            drawOval(
                Color(0xFFA8CAFF).copy(alpha = 0.55f * alpha),
                topLeft,
                Size(w, h),
                style = Stroke(2f),
            )
        }
    }

    private fun DrawScope.drawSmile(state: SplashState) {
        if (state.smileAlpha <= 0.004f || state.smile <= 0f) return
        val glow = ((state.smile - 0.45f) / 0.55f).coerceIn(0f, 1f).pow(1.3f) * state.smileAlpha
        if (glow > 0.01f) {
            // Stacked strokes approximate a blur; one wide stroke reads as a gray band.
            drawSmileSegment(state.smile, Color.White.copy(alpha = 0.10f * glow), 56f)
            drawSmileSegment(state.smile, Color.White.copy(alpha = 0.14f * glow), 40f)
            drawSmileSegment(state.smile, Color.White.copy(alpha = 0.18f * glow), 30f)
        }
        drawSmileSegment(state.smile, Color.White.copy(alpha = state.smileAlpha), 23f)
    }

    private fun DrawScope.drawSmileSegment(fraction: Float, color: Color, width: Float) {
        val path = if (fraction >= 0.999f) {
            smile
        } else {
            smileSegment.reset()
            smileMeasure.getSegment(0f, smileMeasure.length * fraction, smileSegment, true)
            smileSegment
        }
        drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawSmirk(state: SplashState) {
        val alpha = state.smirkAlpha
        if (alpha <= 0.01f) return
        drawPath(SMIRK, Color.White.copy(alpha = 0.45f * alpha), style = Stroke(28f, cap = StrokeCap.Round))
        drawPath(SMIRK, Color.White.copy(alpha = alpha), style = Stroke(16f, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawMouth(state: SplashState) {
        val alpha = state.mouthOpenAlpha
        if (alpha <= 0.01f) return
        val w = 118f * state.mouthOpenScale * (140f / 300f)
        val h = 162f * state.mouthOpenScale * (204f / 380f)
        val center = Offset(340f + 20f, 481f + 225f + state.breath)
        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
        drawOval(
            Color.White.copy(alpha = 0.14f * alpha),
            Offset(topLeft.x - 8f, topLeft.y - 8f),
            Size(w + 16f, h + 16f),
        )
        drawOval(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = alpha),
                0.45f to Color.White.copy(alpha = alpha),
                1f to Color(0xFFCBDEFF).copy(alpha = alpha),
                startY = topLeft.y,
                endY = topLeft.y + h,
            ),
            topLeft = topLeft,
            size = Size(w, h),
        )
    }

    private fun DrawScope.drawMarks(state: SplashState) {
        val alpha = state.marksAlpha
        if (alpha <= 0.01f) return
        val full = 150f * state.marksScale
        val scale = full / 240f
        val ox = 598f - full / 2f
        val oy = 318f - full / 2f
        val color = Color.White.copy(alpha = alpha)
        drawLine(
            color,
            Offset(ox + 52f * scale, oy + 150f * scale),
            Offset(ox + 96f * scale, oy + 44f * scale),
            strokeWidth = 24f * scale,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(ox + 138f * scale, oy + 132f * scale),
            Offset(ox + 196f * scale, oy + 86f * scale),
            strokeWidth = 24f * scale,
            cap = StrokeCap.Round,
        )
    }

    private fun DrawScope.drawRipples(t: Float) {
        for (i in 0 until 4) {
            val p = (t - RIPPLE_STARTS[i]) / RIPPLE_LIVES[i]
            if (p <= 0f || p >= 1f) continue
            val radius = RIPPLE_FROM[i] + (RIPPLE_TO[i] - RIPPLE_FROM[i]) * easeOut(p)
            val remain = 1f - p
            val alpha = RIPPLE_ALPHA[i] * remain * sqrt(remain)
            drawCircle(
                Color(0xFFA0CDFF).copy(alpha = alpha),
                radius,
                Offset(360f, 565f),
                style = Stroke(3f),
            )
        }
    }

    private fun DrawScope.drawSparkles(t: Float) {
        for (i in 0 until 5) {
            val p = (t - SPARKLE_STARTS[i]) / 0.55f
            if (p <= 0f || p >= 1f) continue
            val alpha = sin((PI * p).toFloat()).pow(1.2f)
            val size = SPARKLE_SIZES[i] * (0.5f + 0.8f * easeOut(p))
            drawStar(SPARKLE_X[i], SPARKLE_Y[i], size, alpha)
        }
        for (i in 0 until 3) {
            val p = t - TWINKLE_STARTS[i]
            if (p <= 0f || p >= 1f) continue
            val alpha = sin((PI * p).toFloat()) * 0.8f
            val size = TWINKLE_SIZES[i] * (0.7f + 0.3f * p)
            drawStar(TWINKLE_X[i], TWINKLE_Y[i], size, alpha)
        }
    }

    private fun DrawScope.drawStar(cx: Float, cy: Float, size: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        withTransform({
            translate(cx - size / 2f, cy - size / 2f)
            scale(size / 200f, size / 200f, Offset.Zero)
        }) {
            drawPath(STAR, Color.White.copy(alpha = alpha))
        }
    }

    private fun DrawScope.drawFlash(t: Float) {
        val p = (t - 2.22f) / 0.20f
        if (p <= 0f || p >= 1f) return
        val alpha = 0.38f * (1f - p) * (1f - p)
        drawRect(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = alpha),
                0.35f to Color.White.copy(alpha = alpha * 0.45f),
                1f to Color.Transparent,
                center = Offset(750f, 750f),
                radius = 900f,
            ),
            topLeft = Offset(-100f, -100f),
            size = Size(DESIGN_W + 200f, DESIGN_H + 200f),
        )
    }

    private fun DrawScope.drawSweeps(t: Float) {
        for (start in SWEEP_STARTS) {
            val p = (t - start) / 0.62f
            if (p <= 0f || p >= 1f) continue
            val alpha = sin((PI * p).toFloat()).pow(1.5f) * 0.9f * 0.18f
            val x = -360f + 1450f * easeInOut(p)
            withTransform({ rotate(16f, Offset(x + 150f, 640f)) }) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = alpha),
                        1f to Color.Transparent,
                        startX = x,
                        endX = x + 300f,
                    ),
                    topLeft = Offset(x, -200f),
                    size = Size(300f, 1680f),
                )
            }
        }
    }

    private fun DrawScope.drawWordmark(state: SplashState) {
        if (state.wordReveal[0] <= 0f) return
        val left = 360f - WORD_WIDTH / 2f

        wordPaint.style = Paint.Style.STROKE
        wordPaint.strokeWidth = 10f
        wordPaint.alpha = 255
        val glow = state.wordReveal[4]
        if (glow > 0.01f) {
            wordPaint.color = Color(0xFF5D9CFF).copy(alpha = 0.4f * glow).toArgb()
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.drawText("cloud", left, baseline, wordPaint)
                native.drawText("3", left + letterX[5], baseline, wordPaint)
            }
        }

        wordPaint.style = Paint.Style.FILL
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (i in 0 until 6) {
                val p = state.wordReveal[i]
                if (p <= 0f) continue
                val alpha = easeOut(p)
                val dy = 18f * (1f - alpha)
                wordPaint.color = if (i == 5) 0xFFBBD5FF.toInt() else 0xFFFFFFFF.toInt()
                wordPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                if (i < 5) {
                    native.drawText(WORD_CHARS, i, 1, left + letterX[i], baseline + dy, wordPaint)
                } else {
                    native.drawText("3", left + letterX[5], baseline + dy, wordPaint)
                }
            }
        }
        wordPaint.alpha = 255

        val underline = state.underline
        if (underline > 0.001f) {
            val half = 76f * easeOut(underline)
            val y = WORD_TOP + WORD_BOX_HEIGHT + 20f
            drawLine(
                Color(0x8C78AAFF),
                Offset(360f - half, y),
                Offset(360f + half, y),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color(0xF2DCEBFF),
                Offset(360f - half, y),
                Offset(360f + half, y),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Plays the 3 s soundtrack unless the phone is on silent/vibrate. */
private class SplashAudio(private val context: Context) {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    fun start() {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    player?.pause()
                }
            }
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = request
        player = runCatching {
            MediaPlayer.create(context, R.raw.splash)?.apply { start() }
        }.getOrNull()
    }

    fun stop() {
        player?.release()
        player = null
        focus?.let { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focus = null
    }
}
