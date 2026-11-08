package com.kodastream.app.manga.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSliderTest {
    @Test
    fun firstPageIsTheFirstSliderValueInEveryDirectionCombination() {
        for (uiRtl in listOf(false, true)) {
            for (readingRtl in listOf(false, true)) {
                assertEquals(
                    "uiRtl=$uiRtl readingRtl=$readingRtl",
                    0f,
                    sliderValueForPage(0, 27, readingRtl, uiRtl),
                )
                assertEquals(
                    0,
                    pageFromSliderValue(0f, 27, readingRtl, uiRtl),
                )
            }
        }
    }

    @Test
    fun lastPageIsTheLastSliderValueAndRoundTrips() {
        for (uiRtl in listOf(false, true)) {
            for (readingRtl in listOf(false, true)) {
                val value = sliderValueForPage(26, 27, readingRtl, uiRtl)
                assertEquals(26f, value)
                assertEquals(26, pageFromSliderValue(value, 27, readingRtl, uiRtl))
            }
        }
    }

    @Test
    fun everyPageRoundTripsThroughBothDirections() {
        for (readingRtl in listOf(false, true)) {
            for (page in 0 until 27) {
                val value = sliderValueForPage(page, 27, readingRtl, uiRtl = false)
                assertEquals(page, pageFromSliderValue(value, 27, readingRtl, uiRtl = true))
            }
        }
    }

    @Test
    fun onePageAndZeroPagesMapToZero() {
        assertEquals(0f, sliderValueForPage(4, 1, readingRtl = true, uiRtl = false))
        assertEquals(0f, sliderValueForPage(0, 0, readingRtl = false, uiRtl = true))
        assertEquals(0, pageFromSliderValue(0.7f, 1, readingRtl = true, uiRtl = false))
        assertEquals(0, pageFromSliderValue(5f, 0, readingRtl = false, uiRtl = false))
    }

    @Test
    fun valuesOutsideThePageRangeAreClamped() {
        assertEquals(0f, sliderValueForPage(-3, 10, readingRtl = false, uiRtl = false))
        assertEquals(9f, sliderValueForPage(40, 10, readingRtl = false, uiRtl = false))
        assertEquals(0, pageFromSliderValue(-9f, 10, readingRtl = false, uiRtl = false))
        assertEquals(9, pageFromSliderValue(40f, 10, readingRtl = false, uiRtl = false))
    }
}
