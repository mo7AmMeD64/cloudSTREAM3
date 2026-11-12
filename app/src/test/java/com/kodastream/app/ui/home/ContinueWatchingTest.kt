package com.kodastream.app.ui.home

import com.kodastream.app.data.HistoryStore.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingTest {

    private fun createEntry(
        key: String = "provider|url",
        positionMs: Long = 10_000L,
        durationMs: Long = 100_000L,
        lastWatchedAt: Long = 1_000L,
        completed: Boolean = false,
    ): HistoryEntry = HistoryEntry(
        key = key,
        apiName = "provider",
        url = "https://example.com/details",
        title = key,
        data = "video-data",
        resumeKey = "resume-key",
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatchedAt = lastWatchedAt,
        completed = completed,
    )

    @Test
    fun continueWatching_empty() {
        val result = continueWatching(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun continueWatching_allCompleted() {
        val entries = listOf(
            createEntry(key = "manual_completed", completed = true, positionMs = 20_000L, durationMs = 100_000L),
            createEntry(key = "progress_completed", completed = false, positionMs = 90_000L, durationMs = 100_000L), // 90% is complete
            createEntry(key = "progress_over_completed", completed = false, positionMs = 95_000L, durationMs = 100_000L),
        )
        val result = continueWatching(entries)
        assertTrue(result.isEmpty())
    }

    @Test
    fun continueWatching_oneAbove5s() {
        val entries = listOf(
            createEntry(key = "valid_entry", positionMs = 6_000L, durationMs = 100_000L),
        )
        val result = continueWatching(entries)
        assertEquals(1, result.size)
        assertEquals("valid_entry", result.first().key)
    }

    @Test
    fun continueWatching_allUnder5s() {
        val entries = listOf(
            createEntry(key = "under_1s", positionMs = 1_000L, durationMs = 100_000L),
            createEntry(key = "exact_5s", positionMs = 5_000L, durationMs = 100_000L),
            createEntry(key = "zero_position", positionMs = 0L, durationMs = 100_000L),
        )
        val result = continueWatching(entries)
        assertTrue(result.isEmpty())
    }

    @Test
    fun continueWatching_moreThan10Takes10Newest() {
        // 15 valid entries with timestamps 100, 200, ..., 1500
        val entries = (1..15).map { i ->
            createEntry(
                key = "item-$i",
                positionMs = 10_000L,
                durationMs = 100_000L,
                lastWatchedAt = i * 100L,
            )
        }
        val result = continueWatching(entries)
        assertEquals(10, result.size)
        // Expected newest 10 in descending order: 15 down to 6
        val expectedKeys = (15 downTo 6).map { "item-$it" }
        assertEquals(expectedKeys, result.map { it.key })
    }

    @Test
    fun continueWatching_skipsInvalidDuration() {
        val entries = listOf(
            createEntry(key = "zero_duration", positionMs = 10_000L, durationMs = 0L),
            createEntry(key = "negative_duration", positionMs = 10_000L, durationMs = -100L),
        )
        val result = continueWatching(entries)
        assertTrue(result.isEmpty())
    }
}
