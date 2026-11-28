package com.kodastream.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BannerCropTest {
    @Test
    fun centeredCropFillsTheFrame() {
        val rect = cropRect(
            frameWidth = 300,
            frameHeight = 100,
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(0, rect.left)
        assertEquals(333, rect.top)
        assertEquals(1000, rect.width)
        assertEquals(333, rect.height)
    }

    @Test
    fun panStopsAtTheBitmapEdge() {
        val rect = cropRect(
            frameWidth = 300,
            frameHeight = 100,
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            zoom = 1f,
            panX = 0f,
            panY = 1000f,
        )
        assertEquals(0, rect.top)
        assertEquals(333, rect.height)
    }

    @Test
    fun zoomTakesASmallerSourceRegion() {
        val rect = cropRect(
            frameWidth = 300,
            frameHeight = 100,
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            zoom = 2f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(250, rect.left)
        assertEquals(417, rect.top)
        assertEquals(500, rect.width)
        assertEquals(167, rect.height)
    }
}
