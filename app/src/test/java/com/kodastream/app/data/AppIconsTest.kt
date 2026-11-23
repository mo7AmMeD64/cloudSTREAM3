package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconsTest {

    @Test
    fun knownIconsKeepTheirId() {
        AppIcons.options.forEach { option ->
            assertEquals(option.id, AppIcons.resolve(option.id))
        }
    }

    @Test
    fun removedIconFallsBackToDefault() {
        assertEquals(AppIcons.DEFAULT_ID, AppIcons.resolve("4"))
        assertEquals(AppIcons.DEFAULT_ID, AppIcons.resolve("5"))
        assertEquals(AppIcons.DEFAULT_ID, AppIcons.resolve(null))
    }
}
