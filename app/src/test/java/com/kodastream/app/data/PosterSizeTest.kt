package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterSizeTest {
    @Test
    fun stepsMapToTheirMinimumWidths() {
        assertEquals(88f, posterMinWidth(0).value, 0f)
        assertEquals(104f, posterMinWidth(1).value, 0f)
        assertEquals(128f, posterMinWidth(2).value, 0f)
        assertEquals(160f, posterMinWidth(3).value, 0f)
        assertEquals(200f, posterMinWidth(4).value, 0f)
    }

    @Test
    fun defaultStepIsTheSecondWidth() {
        assertEquals(1, POSTER_SIZE_DEFAULT_STEP)
        assertEquals(104f, posterMinWidth(POSTER_SIZE_DEFAULT_STEP).value, 0f)
    }

    @Test
    fun badStoredStepsFallBackToTheDefault() {
        assertEquals(POSTER_SIZE_DEFAULT_STEP, posterStepOrFallback(null))
        assertEquals(POSTER_SIZE_DEFAULT_STEP, posterStepOrFallback(-1))
        assertEquals(POSTER_SIZE_DEFAULT_STEP, posterStepOrFallback(POSTER_WIDTH_STEPS.size))
        assertEquals(POSTER_SIZE_DEFAULT_STEP, posterStepOrFallback(42))
        assertEquals(104f, posterMinWidth(42).value, 0f)
    }
}
