package com.kodastream.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSpaceTest {

    @Test
    fun unknownFreeSpaceNeverBlocks() {
        assertFalse(DownloadLocations.shouldBlockForSpace(-1L, 0L))
        assertFalse(DownloadLocations.shouldBlockForSpace(-1L, 1L))
        assertFalse(DownloadLocations.shouldBlockForSpace(-1L, 100L))
        assertFalse(DownloadLocations.shouldBlockForSpace(-1L, 50L * 1024 * 1024))
        assertFalse(DownloadLocations.shouldBlockForSpace(-5L, 100L))
    }

    @Test
    fun zeroFreeSpaceBlocksWhenBytesNeeded() {
        assertFalse(DownloadLocations.shouldBlockForSpace(0L, 0L))
        assertTrue(DownloadLocations.shouldBlockForSpace(0L, 1L))
        assertTrue(DownloadLocations.shouldBlockForSpace(0L, 100L))
    }

    @Test
    fun oneByteFreeSpaceBlocksOnlyWhenMoreNeeded() {
        assertFalse(DownloadLocations.shouldBlockForSpace(1L, 0L))
        assertFalse(DownloadLocations.shouldBlockForSpace(1L, 1L))
        assertTrue(DownloadLocations.shouldBlockForSpace(1L, 100L))
    }

    @Test
    fun hundredBytesFreeSpaceBlocksOnlyWhenMoreNeeded() {
        assertFalse(DownloadLocations.shouldBlockForSpace(100L, 0L))
        assertFalse(DownloadLocations.shouldBlockForSpace(100L, 1L))
        assertFalse(DownloadLocations.shouldBlockForSpace(100L, 100L))
        assertTrue(DownloadLocations.shouldBlockForSpace(100L, 101L))
    }

    @Test
    fun hlsCushionBoundaryChecks() {
        val cushion = 50L * 1024 * 1024
        assertTrue(DownloadLocations.shouldBlockForSpace(cushion - 1L, cushion))
        assertFalse(DownloadLocations.shouldBlockForSpace(cushion, cushion))
        assertFalse(DownloadLocations.shouldBlockForSpace(cushion + 1L, cushion))
    }

    @Test
    fun negativeRemainingNeverBlocks() {
        assertFalse(DownloadLocations.shouldBlockForSpace(0L, -1L))
        assertFalse(DownloadLocations.shouldBlockForSpace(100L, -100L))
    }
}
