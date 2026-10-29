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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val writer: () -> Unit,
) {
    @Volatile
    var isDirty: Boolean = false
        private set

    private var job: Job? = null

    /**
     * Marks dirty and schedules a write after [delayMs] of inactivity.
     * If a write was already pending, its countdown is reset.
     */
    fun schedule() {
        synchronized(this) {
            isDirty = true
            job?.cancel()
            job = scope.launch {
                delay(delayMs)
                synchronized(this@Debouncer) {
                    if (isDirty) {
                        isDirty = false
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
            isDirty = false
        }
    }
}
