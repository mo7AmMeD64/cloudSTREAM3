package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HistoryStoreDebounceTest {

    private fun waitUntil(timeoutMs: Long = 1000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun debouncesMultipleCalls() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 40L) {
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
    fun resetsTimerOnSubsequentCalls() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 60L) {
            writeCount.incrementAndGet()
        }

        debouncer.schedule()
        Thread.sleep(35)
        // Calling schedule again should reset the 60ms timer
        debouncer.schedule()

        Thread.sleep(35) // 70ms since first call, but only 35ms since second call
        assertEquals(0, writeCount.get())
        assertTrue(debouncer.isDirty)

        waitUntil { writeCount.get() == 1 }
        assertEquals(1, writeCount.get())
        assertFalse(debouncer.isDirty)
    }

    @Test
    fun flushWritesImmediatelyIfDirty() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 1000L) {
            writeCount.incrementAndGet()
        }

        debouncer.schedule()
        assertTrue(debouncer.isDirty)

        // flush must execute synchronously immediately
        debouncer.flush()
        assertEquals(1, writeCount.get())
        assertFalse(debouncer.isDirty)

        // Wait to verify that the scheduled coroutine was cancelled and does not run again
        Thread.sleep(50)
        assertEquals(1, writeCount.get())
    }

    @Test
    fun flushDoesNothingIfNotDirty() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 1000L) {
            writeCount.incrementAndGet()
        }

        assertFalse(debouncer.isDirty)
        debouncer.flush()
        assertEquals(0, writeCount.get())
    }

    @Test
    fun cancelClearsDirtyAndCancelsScheduledWrite() {
        val writeCount = AtomicInteger(0)
        val debouncer = Debouncer(delayMs = 50L) {
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
        val debouncer = Debouncer(delayMs = 40L) {
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
