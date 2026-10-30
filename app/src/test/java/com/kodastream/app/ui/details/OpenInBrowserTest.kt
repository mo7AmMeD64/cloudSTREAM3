package com.kodastream.app.ui.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenInBrowserTest {

    @Test
    fun canOpenInBrowserReturnsTrueForHttpAndHttps() {
        assertTrue(canOpenInBrowser("http://example.com"))
        assertTrue(canOpenInBrowser("https://example.com"))
        assertTrue(canOpenInBrowser("http://provider.org/title/123"))
        assertTrue(canOpenInBrowser("https://subdomain.provider.tv/movie/watch?ep=1"))
    }

    @Test
    fun canOpenInBrowserReturnsFalseForNonHttpProtocols() {
        assertFalse(canOpenInBrowser("ftp://ftp.example.com"))
        assertFalse(canOpenInBrowser("magnet:?xt=urn:btih:abc"))
        assertFalse(canOpenInBrowser("file:///sdcard/Downloads/test.mp4"))
        assertFalse(canOpenInBrowser("content://media/external/video/media/1"))
        assertFalse(canOpenInBrowser("javascript:alert(1)"))
    }

    @Test
    fun canOpenInBrowserReturnsFalseForNullAndMalformedStrings() {
        assertFalse(canOpenInBrowser(null))
        assertFalse(canOpenInBrowser(""))
        assertFalse(canOpenInBrowser("   "))
        assertFalse(canOpenInBrowser("http"))
        assertFalse(canOpenInBrowser("https"))
        assertFalse(canOpenInBrowser("htt://typo.com"))
        assertFalse(canOpenInBrowser("www.example.com"))
    }
}
