package com.kodastream.app.data

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The five minimum card widths the poster size control steps through. */
val POSTER_WIDTH_STEPS = listOf(88.dp, 104.dp, 128.dp, 160.dp, 200.dp)

/** The second step (104dp), the size a phone should start with. */
const val POSTER_SIZE_DEFAULT_STEP = 1

/** A missing or out-of-range stored step falls back to the default. */
fun posterStepOrFallback(step: Int?): Int =
    step?.takeIf { it in POSTER_WIDTH_STEPS.indices } ?: POSTER_SIZE_DEFAULT_STEP

fun posterMinWidth(step: Int): Dp = POSTER_WIDTH_STEPS[posterStepOrFallback(step)]
