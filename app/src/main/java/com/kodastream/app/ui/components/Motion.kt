package com.kodastream.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically

/**
 * Koda's motion presets: anything a finger drives is a spring, and bouncy is
 * the default rather than the exception. `tween()` stays reserved for
 * crossfades and timed reveals, which is the only place the extra lines here
 * appear.
 */

/** The default touch spring: visible bounce, settles quickly. */
fun <T> bouncySpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** For motion that must not overshoot: position changes, sheet anchors. */
fun <T> softSpring(): SpringSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * The staggered entrance list content plays on arrival: a short fade plus a
 * small rise, one step later per index and capped so long lists do not wait
 * behind their own animation.
 */
fun staggeredEnter(index: Int, stepMs: Int = 40): EnterTransition =
    fadeIn(tween(durationMillis = 220, delayMillis = (index * stepMs).coerceAtMost(320))) +
        slideInVertically(animationSpec = softSpring()) { it / 8 }
