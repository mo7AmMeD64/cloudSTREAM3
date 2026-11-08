package com.kodastream.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCategoryTest {
    @Test
    fun idsAreUniqueAndLowerCase() {
        val ids = SettingsCategory.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it == it.lowercase() && it.isNotBlank() })
    }

    @Test
    fun everyCategoryRoundTripsThroughItsRoute() {
        SettingsCategory.entries.forEach { category ->
            val route = SettingsCategory.route(category)
            assertEquals("settings_page/${category.id}", route)
            assertEquals(category, SettingsCategory.fromId(route.substringAfterLast('/')))
        }
    }

    @Test
    fun unknownOrMissingIdsResolveToNothing() {
        assertNull(SettingsCategory.fromId(null))
        assertNull(SettingsCategory.fromId(""))
        assertNull(SettingsCategory.fromId("Player"))
        assertNull(SettingsCategory.fromId("nope"))
    }

    @Test
    fun routePatternCarriesTheArgumentName() {
        assertEquals("settings_page/{${SettingsCategory.ARG_ID}}", SettingsCategory.ROUTE)
    }

    @Test
    fun clustersListEveryCategoryExactlyOnce() {
        val flat = SettingsCategory.clusters.flatten()
        assertEquals(SettingsCategory.entries.size, flat.size)
        assertEquals(SettingsCategory.entries.toSet(), flat.toSet())
        assertTrue(SettingsCategory.clusters.none { it.isEmpty() })
    }
}
