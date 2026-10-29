package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPrefsTest {
    @Test
    fun allowInvalidSslKeyConstant() {
        assertEquals("allow_invalid_ssl", AppPrefs.KEY_ALLOW_INVALID_SSL)
    }
}
