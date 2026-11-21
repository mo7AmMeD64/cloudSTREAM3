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
    fun animatedBackgroundKeyConstant() {
        assertEquals("animated_background", AppPrefs.KEY_ANIMATED_BACKGROUND)
    }

    @Test
    fun translationKeyConstants() {
        assertEquals("translation_api_key", AppPrefs.KEY_TRANSLATION_API_KEY)
        assertEquals("translation_target", AppPrefs.KEY_TRANSLATION_TARGET)
        assertEquals("translation_model", AppPrefs.KEY_TRANSLATION_MODEL)
        assertEquals("ar", AppPrefs.TRANSLATION_TARGET_ARABIC)
        assertEquals("en", AppPrefs.TRANSLATION_TARGET_ENGLISH)
        assertEquals("gemini-2.5-flash", AppPrefs.DEFAULT_TRANSLATION_MODEL)
    }

    @Test
    fun appIconKeyConstant() {
        assertEquals("app_icon", AppPrefs.KEY_APP_ICON)
    }
}
