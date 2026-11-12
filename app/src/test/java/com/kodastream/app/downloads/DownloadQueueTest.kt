package com.kodastream.app.downloads

import com.kodastream.app.data.DownloadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueTest {

    private fun createDownload(id: String, state: String = DownloadStore.STATE_QUEUED): DownloadStore.Download {
        return DownloadStore.Download(
            id = id,
            apiName = "provider",
            url = "https://example.com/$id",
            title = "Title $id",
            fileName = "$id.mp4",
            linkUrl = "https://example.com/video/$id.mp4",
            state = state,
        )
    }

    @Test
    fun calculateAvailableSlotsCorrectly() {
        assertEquals(2, calculateAvailableSlots(maxConcurrent = 2, activeCount = 0))
        assertEquals(1, calculateAvailableSlots(maxConcurrent = 2, activeCount = 1))
        assertEquals(0, calculateAvailableSlots(maxConcurrent = 2, activeCount = 2))
        assertEquals(0, calculateAvailableSlots(maxConcurrent = 2, activeCount = 3))
        assertEquals(3, calculateAvailableSlots(maxConcurrent = 3, activeCount = 0))
        assertEquals(0, calculateAvailableSlots(maxConcurrent = 1, activeCount = 1))
    }

    @Test
    fun selectCandidatesRespectsAvailableSlots() {
        val items = listOf(
            createDownload("ep1"),
            createDownload("ep2"),
            createDownload("ep3"),
        )
        val selected = selectQueuedCandidates(
            queued = items,
            activeIds = emptySet(),
            availableSlots = 2,
        )
        assertEquals(2, selected.size)
        assertEquals("ep1", selected[0].id)
        assertEquals("ep2", selected[1].id)
    }

    @Test
    fun selectCandidatesFiltersOutActiveItems() {
        val items = listOf(
            createDownload("ep1"),
            createDownload("ep2"),
            createDownload("ep3"),
        )
        val selected = selectQueuedCandidates(
            queued = items,
            activeIds = setOf("ep1"),
            availableSlots = 2,
        )
        assertEquals(2, selected.size)
        assertEquals("ep2", selected[0].id)
        assertEquals("ep3", selected[1].id)
    }

    @Test
    fun selectCandidatesFiltersOutNonQueuedStates() {
        val items = listOf(
            createDownload("ep1", DownloadStore.STATE_DOWNLOADING),
            createDownload("ep2", DownloadStore.STATE_PAUSED),
            createDownload("ep3", DownloadStore.STATE_QUEUED),
            createDownload("ep4", DownloadStore.STATE_DONE),
            createDownload("ep5", DownloadStore.STATE_FAILED),
            createDownload("ep6", DownloadStore.STATE_QUEUED),
        )
        val selected = selectQueuedCandidates(
            queued = items,
            activeIds = emptySet(),
            availableSlots = 5,
        )
        assertEquals(2, selected.size)
        assertEquals("ep3", selected[0].id)
        assertEquals("ep6", selected[1].id)
    }

    @Test
    fun selectCandidatesReturnsEmptyWhenNoSlotsAvailable() {
        val items = listOf(
            createDownload("ep1"),
            createDownload("ep2"),
        )
        val selected = selectQueuedCandidates(
            queued = items,
            activeIds = emptySet(),
            availableSlots = 0,
        )
        assertTrue(selected.isEmpty())
    }
}
