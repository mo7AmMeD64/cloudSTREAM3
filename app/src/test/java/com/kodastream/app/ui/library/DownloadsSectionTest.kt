package com.kodastream.app.ui.library

import com.kodastream.app.data.DownloadStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSectionTest {

    @Test
    fun resolveDownloadErrorLabelReturnsActualErrorWhenPresent() {
        assertEquals(
            "HTTP 403 Forbidden",
            resolveDownloadErrorLabel("HTTP 403 Forbidden", fallback = "Failed"),
        )
        assertEquals(
            "Connection reset by peer",
            resolveDownloadErrorLabel("Connection reset by peer", fallback = "Failed"),
        )
    }

    @Test
    fun resolveDownloadErrorLabelFallsBackWhenNull() {
        assertEquals(
            "Download failed",
            resolveDownloadErrorLabel(null, fallback = "Download failed"),
        )
    }

    @Test
    fun resolveDownloadErrorLabelFallsBackWhenBlank() {
        assertEquals(
            "Download failed",
            resolveDownloadErrorLabel("   ", fallback = "Download failed"),
        )
    }

    @Test
    fun queuedDownloadsFiltersOnlyQueuedState() {
        val dl1 = DownloadStore.Download(
            id = "1",
            apiName = "test",
            url = "http://test/1",
            title = "Test 1",
            linkUrl = "http://link/1",
            fileName = "test1.mp4",
            state = DownloadStore.STATE_QUEUED,
        )
        val dl2 = DownloadStore.Download(
            id = "2",
            apiName = "test",
            url = "http://test/2",
            title = "Test 2",
            linkUrl = "http://link/2",
            fileName = "test2.mp4",
            state = DownloadStore.STATE_DOWNLOADING,
        )
        val dl3 = DownloadStore.Download(
            id = "3",
            apiName = "test",
            url = "http://test/3",
            title = "Test 3",
            linkUrl = "http://link/3",
            fileName = "test3.mp4",
            state = DownloadStore.STATE_DONE,
        )
        val dl4 = DownloadStore.Download(
            id = "4",
            apiName = "test",
            url = "http://test/4",
            title = "Test 4",
            linkUrl = "http://link/4",
            fileName = "test4.mp4",
            state = DownloadStore.STATE_QUEUED,
        )

        val queued = queuedDownloads(listOf(dl1, dl2, dl3, dl4))
        assertEquals(listOf(dl1, dl4), queued)
    }
}
