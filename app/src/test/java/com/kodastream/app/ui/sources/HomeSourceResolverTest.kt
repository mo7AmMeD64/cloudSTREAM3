package com.kodastream.app.ui.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSourceResolverTest {

    private fun resolve(
        saved: String?,
        current: String?,
        available: List<String>,
        userPicked: Boolean = false,
        startupDone: Boolean = true,
    ): String? = resolveHomeSource(saved, current, userPicked, available, startupDone)

    @Test
    fun savedSourceArrivingLateReplacesTheFallbackOnce() {
        // Startup already finished without the saved source: fall back once.
        val fallback = resolve("Cima Cloud", null, listOf("EgyDead"))
        assertEquals("EgyDead", fallback)

        // The saved extension finishes loading later: go back to it, with no tap.
        val switched = resolve("Cima Cloud", fallback, listOf("Cima Cloud", "EgyDead"))
        assertEquals("Cima Cloud", switched)

        // Applying the resolver again must not flap back to the fallback.
        assertEquals(
            "Cima Cloud",
            resolve("Cima Cloud", switched, listOf("Cima Cloud", "EgyDead")),
        )
    }

    @Test
    fun savedSourceMissingWaitsForStartup() {
        // Not available yet, startup still running: no other provider's content.
        assertNull(resolve("Cima Cloud", null, listOf("EgyDead"), startupDone = false))
        // After startup the fallback is allowed.
        assertEquals("EgyDead", resolve("Cima Cloud", null, listOf("EgyDead"), startupDone = true))
    }

    @Test
    fun userPickWinsOverSaved() {
        assertEquals(
            "EgyDead",
            resolve("Cima Cloud", "EgyDead", listOf("Cima Cloud", "EgyDead"), userPicked = true),
        )
    }

    @Test
    fun removedSavedSourceFallsBackButIsStillSaved() {
        assertEquals("EgyDead", resolve("Cima Cloud", "EgyDead", listOf("EgyDead")))
        // Reinstalling it must bring Home back without another tap.
        assertEquals(
            "Cima Cloud",
            resolve("Cima Cloud", "EgyDead", listOf("Cima Cloud", "EgyDead")),
        )
    }

    @Test
    fun pickedSourceThatDisappearsFallsBack() {
        assertEquals(
            "EgyDead",
            resolve("Cima Cloud", "Cima Cloud", listOf("EgyDead"), userPicked = true),
        )
    }

    @Test
    fun emptyProviderListHasNoSource() {
        assertNull(resolve(null, null, emptyList()))
        assertNull(resolve("Cima Cloud", "Cima Cloud", emptyList(), userPicked = true))
    }

    @Test
    fun repeatedResolutionConverges() {
        var current: String? = null
        repeat(20) {
            current = resolve("Cima Cloud", current, listOf("EgyDead"))
            assertEquals("EgyDead", current)
        }
    }

    @Test
    fun unchangedSelectionReturnsTheSameValue() {
        repeat(20) {
            assertEquals("Cima Cloud", resolve("Cima Cloud", "Cima Cloud", listOf("Cima Cloud")))
        }
    }
}
