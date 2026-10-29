package com.kodastream.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pure debouncer for scheduling writes after [delayMs] of inactivity.
 *
 * Calls to [schedule] mark the debouncer dirty and push the write back by [delayMs].
 * Calls to [flush] immediately execute the [writer] synchronously on the calling thread
 * if dirty, cancelling any pending scheduled coroutine.
 * Calls to [cancel] abort any pending scheduled write and clear the dirty flag.
 */
class Debouncer(
    val delayMs: Long,
    val maxDelayMs: Long = 0L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val writer: () -> Unit,
) {
    @Volatile
    var isDirty: Boolean = false
        private set

    private var job: Job? = null
    private var firstScheduleEpochMs: Long = 0L

    /**
     * Marks dirty and schedules a write after [delayMs] of inactivity, or after
     * at most [maxDelayMs] from the initial schedule if [maxDelayMs] > 0.
     * If a write was already pending and [maxDelayMs] is not exceeded, its countdown is reset.
     */
    fun schedule() {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (!isDirty || firstScheduleEpochMs == 0L) {
                firstScheduleEpochMs = now
            }
            isDirty = true
            job?.cancel()

            val effectiveDelay = if (maxDelayMs > 0L) {
                val deadline = firstScheduleEpochMs + maxDelayMs
                (deadline - now).coerceIn(0L, delayMs)
            } else {
                delayMs
            }

            job = scope.launch {
                delay(effectiveDelay)
                synchronized(this@Debouncer) {
                    if (isDirty) {
                        isDirty = false
                        firstScheduleEpochMs = 0L
                        job = null
                        writer()
                    }
                }
            }
        }
    }

    /**
     * Executes [writer] synchronously on the calling thread if marked dirty.
     * Cancels any pending scheduled coroutine.
     */
    fun flush() {
        val shouldWrite: Boolean
        synchronized(this) {
            job?.cancel()
            job = null
            firstScheduleEpochMs = 0L
            shouldWrite = isDirty
            isDirty = false
        }
        if (shouldWrite) {
            writer()
        }
    }

    /**
     * Cancels any pending scheduled coroutine and resets the dirty flag without writing.
     */
    fun cancel() {
        synchronized(this) {
            job?.cancel()
            job = null
            firstScheduleEpochMs = 0L
            isDirty = false
        }
    }
}
