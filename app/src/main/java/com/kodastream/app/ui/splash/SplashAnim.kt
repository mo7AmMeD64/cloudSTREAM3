package com.kodastream.app.ui.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The launch animation as a pure function of time, in authored seconds.
 *
 * Beats were tuned at 4.4 s and are time-compressed to the 3 s splash budget;
 * nothing is cut, everything simply plays 1.47x faster (audio included).
 * Kept free of Android types so it can be unit tested.
 */
object SplashTimeline {
    const val DISPLAY_SECONDS = 3.0f
    const val AUTHORED_SECONDS = 4.4f
    const val AUTHORED_PER_DISPLAY = AUTHORED_SECONDS / DISPLAY_SECONDS

    /** Authored time for the 0..1 progress of the 3 s overlay. */
    fun authoredAt(displayProgress: Float): Float = displayProgress * AUTHORED_SECONDS

    fun state(t: Float): SplashState {
        val squint = squint(t)
        val surprise = surprise(t)
        val breath = sin(TAU * 0.31f * t)

        // Eyes drop in one after the other.
        val eyeAlphaLeft = clamp01((t - 0.36f) / 0.07f)
        val eyeAlphaRight = clamp01((t - 0.43f) / 0.07f)
        val eyeDropLeft = -170f * (1f - spring(t, 0.36f, 2.9f, 8.5f))
        val eyeDropRight = -170f * (1f - spring(t, 0.43f, 2.9f, 8.5f))

        val bob = 6.5f * sin(TAU * 0.30f * t + 0.4f)
        val tilt = 2.2f * sin(TAU * 0.21f * t + 0.6f) - 8f * squint
        var scale = 1f + 0.006f * breath
        if (t > 0.74f) {
            val x = t - 0.74f
            scale += 0.035f * exp(-9f * x) * sin(TAU * 2.3f * x)
        }
        scale += 0.05f * surprise

        val exit = easeInOut(key(t, 4.04f, 4.34f))

        return SplashState(
            time = t,
            squint = squint,
            surprise = surprise,
            look = track(t, LOOK_KEYS),
            breath = breath,
            blink = blink(t),
            eyeAlphaLeft = eyeAlphaLeft,
            eyeAlphaRight = eyeAlphaRight,
            eyeDropLeft = eyeDropLeft,
            eyeDropRight = eyeDropRight,
            bob = bob,
            tilt = tilt,
            scale = scale,
            eyeScale = 1f + 0.13f * surprise,
            smile = smileDraw(t),
            smileAlpha = smileAlpha(t),
            smirkAlpha = smirkAlpha(t),
            mouthOpenAlpha = mouthAlpha(t),
            mouthOpenScale = mouthScale(t),
            marksAlpha = marksAlpha(t),
            marksScale = marksScale(t),
            wordReveal = FloatArray(6) { i -> clamp01((t - (3.06f + i * 0.058f)) / 0.24f) },
            underline = key(t, 3.34f, 3.56f),
            exitScale = 1f + 0.065f * easeInOut(key(t, 3.96f, 4.34f)),
            exitAlpha = 1f - exit,
        )
    }
}

data class SplashState(
    val time: Float,
    val squint: Float,
    val surprise: Float,
    val look: Float,
    val breath: Float,
    val blink: Float,
    val eyeAlphaLeft: Float,
    val eyeAlphaRight: Float,
    val eyeDropLeft: Float,
    val eyeDropRight: Float,
    val bob: Float,
    val tilt: Float,
    val scale: Float,
    val eyeScale: Float,
    val smile: Float,
    val smileAlpha: Float,
    val smirkAlpha: Float,
    val mouthOpenAlpha: Float,
    val mouthOpenScale: Float,
    val marksAlpha: Float,
    val marksScale: Float,
    val wordReveal: FloatArray,
    val underline: Float,
    val exitScale: Float,
    val exitAlpha: Float,
)

private const val TAU = (2.0 * PI).toFloat()

private val LOOK_KEYS = floatArrayOf(
    1.06f, 0f,
    1.16f, -13f,
    1.28f, 13f,
    1.40f, 0f,
    1.90f, -7f,
    1.99f, 0f,
)

private val BLINK_STARTS = floatArrayOf(0.96f, 2.98f, 3.78f)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun easeOut(t: Float): Float {
    val u = 1f - clamp01(t)
    return 1f - u * u * u
}

private fun easeInOut(t: Float): Float {
    val c = clamp01(t)
    if (c < 0.5f) return 4f * c * c * c
    val u = 2f - 2f * c
    return 1f - u * u * u / 2f
}

private fun key(t: Float, from: Float, to: Float) = clamp01((t - from) / (to - from))

private fun spring(t: Float, start: Float, freq: Float, damp: Float): Float {
    if (t <= start) return 0f
    val x = t - start
    return 1f - exp(-damp * x) * cos(TAU * freq * x)
}

/** Keyframe track over flat (time, value) pairs, eased between keys. */
private fun track(t: Float, keys: FloatArray): Float {
    if (t <= keys[0]) return keys[1]
    var i = 0
    while (i + 3 < keys.size) {
        val t0 = keys[i]
        val v0 = keys[i + 1]
        val t1 = keys[i + 2]
        val v1 = keys[i + 3]
        if (t <= t1) return lerp(v0, v1, easeInOut((t - t0) / (t1 - t0)))
        i += 2
    }
    return keys[keys.size - 1]
}

private fun blink(t: Float): Float {
    var f = 1f
    for (start in BLINK_STARTS) {
        if (t >= start && t < start + 0.075f) {
            f = minOf(f, lerp(1f, 0.05f, easeInOut((t - start) / 0.075f)))
        } else if (t >= start + 0.075f && t < start + 0.17f) {
            f = minOf(f, lerp(0.05f, 1f, easeInOut((t - start - 0.075f) / 0.095f)))
        }
    }
    return f
}

private fun squint(t: Float) = when {
    t < 1.58f -> 0f
    t < 1.76f -> easeInOut((t - 1.58f) / 0.18f)
    t < 2.04f -> 1f
    t < 2.16f -> 1f - easeInOut((t - 2.04f) / 0.12f)
    else -> 0f
}

private fun surprise(t: Float) = when {
    t < 2.14f -> 0f
    t < 2.46f -> spring(t, 2.16f, 2.4f, 7f)
    t < 2.64f -> 1f
    t < 2.82f -> 1f - easeInOut((t - 2.64f) / 0.18f)
    else -> 0f
}

private fun smileDraw(t: Float) = when {
    t < 1.02f -> 0f
    t < 1.52f -> easeOut((t - 1.02f) / 0.50f)
    t < 1.56f -> 1f
    t < 2.56f -> 0f
    t < 3.06f -> easeOut((t - 2.56f) / 0.50f)
    else -> 1f
}

private fun smileAlpha(t: Float) = when {
    t < 1.02f -> 0f
    t < 1.54f -> easeOut((t - 1.02f) / 0.10f)
    t < 1.62f -> 1f - easeInOut((t - 1.54f) / 0.08f)
    t < 2.56f -> 0f
    else -> 1f
}

private fun smirkAlpha(t: Float) = when {
    t < 1.64f -> 0f
    t < 1.74f -> easeOut((t - 1.64f) / 0.10f)
    t < 2.02f -> 1f
    t < 2.14f -> 1f - easeInOut((t - 2.02f) / 0.12f)
    else -> 0f
}

private fun mouthAlpha(t: Float) = when {
    t < 2.16f || t >= 2.70f -> 0f
    t < 2.26f -> easeOut((t - 2.16f) / 0.10f)
    t < 2.58f -> 1f
    else -> 1f - easeInOut((t - 2.58f) / 0.12f)
}

private fun mouthScale(t: Float) = when {
    t < 2.26f -> 0.45f + 0.55f * spring(t, 2.16f, 2.6f, 7f)
    t < 2.58f -> 1f + 0.035f * sin(TAU * 4f * (t - 2.26f)) * exp(-(t - 2.26f) / 0.15f)
    else -> 1f - 0.45f * easeInOut((t - 2.58f) / 0.12f)
}

private fun marksAlpha(t: Float) = when {
    t < 2.22f || t >= 2.76f -> 0f
    t < 2.34f -> easeOut((t - 2.22f) / 0.12f)
    t < 2.60f -> 1f
    else -> 1f - easeInOut((t - 2.60f) / 0.16f)
}

private fun marksScale(t: Float) = when {
    t < 2.34f -> 0.45f + 0.55f * spring(t, 2.22f, 2.6f, 7f)
    t < 2.60f -> 1f
    else -> 1f - 0.30f * easeInOut((t - 2.60f) / 0.16f)
}
