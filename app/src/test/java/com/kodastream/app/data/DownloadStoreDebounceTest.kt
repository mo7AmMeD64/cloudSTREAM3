package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadStoreDebounceTest {

    private fun waitUntil(timeoutMs: Long = 1000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun debouncesHighFrequencyProgressUpdates() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 40L, maxDelayMs = 100L) {
            writeCount.incrementAndGet()
        }

        repeat(5) {
            debouncer.schedule()
            Thread.sleep(5)
        }

        assertEquals(0, writeCount.get())
        assertTrue(debouncer.isDirty)

        waitUntil { writeCount.get() == 1 }

        assertEquals(1, writeCount.get())
        assertFalse(debouncer.isDirty)
    }

    @Test
    fun maxDelayEnforcesPeriodicWritesDuringContinuousTicks() {
        val writeCount = AtomicInteger(0)
        // With 40ms delay but 50ms maxDelay, continuous scheduling every 10ms
        // must trigger a write within ~60ms instead of starving indefinitely.
        val debouncer = Debouncer(delayMs = 40L, maxDelayMs = 50L) {
            writeCount.incrementAndGet()
        }

        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 120) {
            debouncer.schedule()
            Thread.sleep(10)
        }

        // Over 120ms with a 50ms ceiling, at least 1 (and up to 2) writes must have occurred
        assertTrue("Expected at least 1 write during continuous ticks, got ${writeCount.get()}", writeCount.get() >= 1)
    }

    @Test
    fun flushWritesImmediatelyIfDirty() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 1000L, maxDelayMs = 2000L) {
            writeCount.incrementAndGet()
        }

        debouncer.schedule()
        assertTrue(debouncer.isDirty)

        // flush must execute synchronously immediately on calling thread
        debouncer.flush()
        assertEquals(1, writeCount.get())
        assertFalse(debouncer.isDirty)

        // Verify the scheduled coroutine was cancelled and does not run again
        Thread.sleep(50)
        assertEquals(1, writeCount.get())
    }

    @Test
    fun flushDoesNothingIfNotDirty() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 1000L, maxDelayMs = 2000L) {
            writeCount.incrementAndGet()
        }

        assertFalse(debouncer.isDirty)
        debouncer.flush()
        assertEquals(0, writeCount.get())
    }

    @Test
    fun cancelClearsDirtyAndCancelsScheduledWrite() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 50L, maxDelayMs = 100L) {
            writeCount.incrementAndGet()
        }

        debouncer.schedule()
        assertTrue(debouncer.isDirty)

        debouncer.cancel()
        assertFalse(debouncer.isDirty)

        Thread.sleep(80)
        assertEquals(0, writeCount.get())
    }

    @Test
    fun subsequentScheduleAfterFlushTriggersNewDebouncedWrite() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 40L, maxDelayMs = 80L) {
            writeCount.incrementAndGet()
        }

        debouncer.schedule()
        debouncer.flush()
        assertEquals(1, writeCount.get())

        debouncer.schedule()
        assertTrue(debouncer.isDirty)

        waitUntil { writeCount.get() == 2 }
        assertEquals(2, writeCount.get())
        assertFalse(debouncer.isDirty)
    }
}
