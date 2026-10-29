package com.kodastream.app.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWifiGatingTest {

    @Test
    fun blockForWifiWhenEnabledOnCellular() {
        assertTrue(shouldBlockForWifi(wifiOnly = true, onWifi = false))
    }

    @Test
    fun allowDownloadWhenWifiOnlyAndOnWifi() {
        assertFalse(shouldBlockForWifi(wifiOnly = true, onWifi = true))
    }

    @Test
    fun allowDownloadWhenWifiOnlyDisabledOnCellular() {
        assertFalse(shouldBlockForWifi(wifiOnly = false, onWifi = false))
    }

    @Test
    fun allowDownloadWhenWifiOnlyDisabledOnWifi() {
        assertFalse(shouldBlockForWifi(wifiOnly = false, onWifi = true))
    }

    @Test
    fun notificationStateWaitingForWifiWhenQueuedOnCellular() {
        val state = resolveDownloadNotificationState(
            hasRunning = false,
            hasQueued = true,
            wifiOnly = true,
            onWifi = false,
        )
        assertEquals(DownloadNotificationState.WAITING_FOR_WIFI, state)
    }

    @Test
    fun notificationStateWaitingForWifiWhenRunningOnCellular() {
        val state = resolveDownloadNotificationState(
            hasRunning = true,
            hasQueued = false,
            wifiOnly = true,
            onWifi = false,
        )
        assertEquals(DownloadNotificationState.WAITING_FOR_WIFI, state)
    }

    @Test
    fun notificationStateDownloadingWhenOnWifi() {
        val state = resolveDownloadNotificationState(
            hasRunning = true,
            hasQueued = true,
            wifiOnly = true,
            onWifi = true,
        )
        assertEquals(DownloadNotificationState.DOWNLOADING, state)
    }

    @Test
    fun notificationStateDownloadingOnCellularWhenWifiOnlyDisabled() {
        val state = resolveDownloadNotificationState(
            hasRunning = true,
            hasQueued = false,
            wifiOnly = false,
            onWifi = false,
        )
        assertEquals(DownloadNotificationState.DOWNLOADING, state)
    }

    @Test
    fun notificationStateQueuedWhenOnWifi() {
        val state = resolveDownloadNotificationState(
            hasRunning = false,
            hasQueued = true,
            wifiOnly = true,
            onWifi = true,
        )
        assertEquals(DownloadNotificationState.QUEUED, state)
    }

    @Test
    fun notificationStateIdleWhenEmpty() {
        val state = resolveDownloadNotificationState(
            hasRunning = false,
            hasQueued = false,
            wifiOnly = true,
            onWifi = false,
        )
        assertEquals(DownloadNotificationState.IDLE, state)
    }
}
