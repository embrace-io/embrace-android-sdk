package io.embrace.android.embracesdk.internal.vitals.fake

import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler

/**
 * Records what was scheduled and lets the test run the pending settle check. Ignores the delay (the test
 * drives time via [org.robolectric.shadows.ShadowSystemClock], and the tracker decides settle-vs-reschedule by reading the clock).
 */
internal class FakeVitalsScheduler : VitalsScheduler {
    private var pending: Runnable? = null
    var scheduleCount = 0
    var lastDelayMs = -1L
    val scheduled: Boolean get() = pending != null

    override fun post(action: Runnable) = action.run()

    override fun scheduleSettle(delayMs: Long, action: Runnable) {
        scheduleCount++
        lastDelayMs = delayMs
        pending = action
    }

    override fun cancelSettle(action: Runnable) {
        if (pending === action) {
            pending = null
        }
    }

    /**
     * Runs the pending settle check, as the scheduler would once the delay elapses.
     */
    fun runPending() = pending?.run() ?: Unit
}
