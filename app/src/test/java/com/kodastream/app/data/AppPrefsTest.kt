package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPrefsTest {
    @Test
    fun allowInvalidSslKeyConstant() {
        assertEquals("allow_invalid_ssl", AppPrefs.KEY_ALLOW_INVALID_SSL)
    }

    @Test
    fun downloadWifiOnlyKeyConstant() {
        assertEquals("download_wifi_only", AppPrefs.KEY_DL_WIFI)
    }

    @Test
    fun downloadMaxConcurrentKeyConstant() {
        assertEquals("download_max_concurrent", AppPrefs.KEY_DL_MAX)
    }

    @Test
    fun uiStyleKeyAndValueConstants() {
        assertEquals("ui_style", AppPrefs.KEY_UI_STYLE)
        assertEquals("material", AppPrefs.UI_STYLE_MATERIAL)
        assertEquals("glass", AppPrefs.UI_STYLE_GLASS)
    }

    @Test
    fun hidePosterTitlesKeyConstant() {
        assertEquals("hide_poster_titles", AppPrefs.KEY_HIDE_POSTER_TITLES)
    }

    @Test
    fun appIconKeyConstant() {
        assertEquals("app_icon", AppPrefs.KEY_APP_ICON)
    }
}
