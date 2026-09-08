package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService

/**
 * Exceeds the longest delay that any [CoalescingWriteQueue] in [SessionPartWriterImpl] is armed
 * with, so that advancing time by this much always makes a debounced write due.
 */
internal val WRITE_DRAIN_TICK_MS: Long = maxOf(
    SessionPartWriterImpl.METADATA_WRITE_DELAY_MS,
    SessionPartWriterImpl.SESSION_SPAN_WRITE_DELAY_MS,
    SessionPartWriterImpl.SPAN_SNAPSHOT_WRITE_DELAY_MS,
)

private const val MAX_DRAIN_ITERATIONS = 100

/**
 * Runs every queued session part write, including the debounced ones, by advancing time until
 * nothing is left scheduled.
 */
internal fun BlockingScheduledExecutorService.drainWrites(tickMs: Long = WRITE_DRAIN_TICK_MS) {
    repeat(MAX_DRAIN_ITERATIONS) {
        moveForwardAndRunBlocked(tickMs)
        if (scheduledTasksCount() == 0) {
            return
        }
    }
    error("session part writes never settled")
}
