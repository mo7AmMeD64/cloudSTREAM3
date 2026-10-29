/*
 * Ported from Nuvio (https://github.com/NuvioMedia/NuvioMobile), GPL-3.0. Adapted for KodaStream.
 * Original: composeApp/src/commonTest/kotlin/com/nuvio/app/features/home/components/HomeHeroSectionTest.kt
 */
package com.kodastream.app.ui.home.hero

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioHeroSectionTest {

    @Test
    fun `first and last indicators select adjacent pages across the loop`() {
        assertEquals(79, heroPageForItem(currentPage = 80, itemIndex = 7, itemCount = 8))
        assertEquals(88, heroPageForItem(currentPage = 87, itemIndex = 0, itemCount = 8))
    }

    @Test
    fun `indicators keep the current page when selecting the active title`() {
        assertEquals(83, heroPageForItem(currentPage = 83, itemIndex = 3, itemCount = 8))
    }

    @Test
    fun `indicators select the closest occurrence of a title`() {
        assertEquals(85, heroPageForItem(currentPage = 83, itemIndex = 5, itemCount = 8))
        assertEquals(81, heroPageForItem(currentPage = 83, itemIndex = 1, itemCount = 8))
    }

    @Test
    fun `mobile hero height stays compact without continue watching`() {
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(452.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `hero layout uses the three tablet breakpoints`() {
        val medium = homeHeroLayout(maxWidthDp = 600f, viewportHeightDp = 1200f)
        assertEquals(true, medium.isTablet)
        assertEquals(348f, medium.heroHeight.value, 0.001f)
        assertEquals(520f, medium.contentMaxWidth.value, 0.001f)

        val expanded = homeHeroLayout(maxWidthDp = 840f, viewportHeightDp = 1200f)
        assertEquals(true, expanded.isTablet)
        assertEquals(386.4f, expanded.heroHeight.value, 0.001f)
        assertEquals(560f, expanded.contentMaxWidth.value, 0.001f)

        val large = homeHeroLayout(maxWidthDp = 1200f, viewportHeightDp = 1200f)
        assertEquals(true, large.isTablet)
        assertEquals(440f, large.heroHeight.value, 0.001f)
        assertEquals(640f, large.contentMaxWidth.value, 0.001f)
    }

    @Test
    fun `swipe direction follows the layout direction`() {
        assertEquals(
            6,
            resolveHeroTargetPage(startPage = 5, pageCount = 10, totalDx = -200f, velocityX = 0f, widthPx = 1000f),
        )
        assertEquals(
            4,
            resolveHeroTargetPage(startPage = 5, pageCount = 10, totalDx = 200f, velocityX = 0f, widthPx = 1000f),
        )
        assertEquals(
            4,
            resolveHeroTargetPage(
                startPage = 5,
                pageCount = 10,
                totalDx = -200f,
                velocityX = 0f,
                widthPx = 1000f,
                isRtl = true,
            ),
        )
        assertEquals(
            6,
            resolveHeroTargetPage(
                startPage = 5,
                pageCount = 10,
                totalDx = 200f,
                velocityX = 0f,
                widthPx = 1000f,
                isRtl = true,
            ),
        )
    }

    @Test
    fun `short drag keeps the current page in both directions`() {
        assertEquals(
            5,
            resolveHeroTargetPage(startPage = 5, pageCount = 10, totalDx = -100f, velocityX = 0f, widthPx = 1000f),
        )
        assertEquals(
            5,
            resolveHeroTargetPage(
                startPage = 5,
                pageCount = 10,
                totalDx = 100f,
                velocityX = 0f,
                widthPx = 1000f,
                isRtl = true,
            ),
        )
    }
}
