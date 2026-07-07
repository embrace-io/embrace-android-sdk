package io.embrace.android.embracesdk.internal.vitals

import android.os.SystemClock

/**
 * A settle channel: detects when a subsystem has gone quiet for an idle threshold. Each settle has a dynamic [threshold] which must pass
 * without [activity](notifyActivity) before [onSettled] is called.
 *
 * This class is not thread-safe and should only be used from a single thread. Callbacks are triggered on the [scheduler] thread.
 */
internal class SettleTracker(
    private val scheduler: VitalsScheduler,
    private val threshold: ThresholdProvider,
    private val onSettled: SettleCallback,
) {

    private val runnable = Runnable { checkSettled() }
    private var active = false

    /**
     * The latest activity baseline seen ([SystemClock.uptimeMillis]) — the most recent [notifyActivity] timestamp. Reset to 0 by [cancel].
     */
    var lastActivityMs = 0L
        private set

    /**
     * Mark the `SettleTracker` as "unsettled" as-of [activityMs] so that [onSettled] will not be called until at least [threshold] millis
     * after [activityMs]. The first event of a moment schedules the settle check, subsequent calls move when [onSettled] will be called.
     */
    fun notifyActivity(activityMs: Long) {
        if (activityMs > lastActivityMs) {
            lastActivityMs = activityMs
        }
        if (!active) {
            // First activity of the moment: nothing is scheduled yet, so schedule once. While active, a
            // check is always pending, so a later event need only move the baseline above.
            active = true
            scheduleCheck()
        }
    }

    /**
     * Ensure that the [SettleTracker] is active and scheduled for an appropriate time. This should be called when there has been no
     * activity, but the settling threshold may have changed. That is: you don't want to change [lastActivityMs] but the time until the
     * [onSettled] may have changed.
     */
    fun reschedule() {
        if (active) {
            scheduleCheck()
        }
    }

    /**
     * Stops tracking and clears the baseline, so the next [notifyActivity] starts a fresh moment.
     */
    fun cancel() {
        active = false
        lastActivityMs = 0L
        scheduler.cancelSettle(runnable)
    }

    private fun scheduleCheck() {
        val delayMs = (lastActivityMs + threshold.thresholdMs() - nowMs()).coerceAtLeast(0L)
        scheduler.scheduleSettle(delayMs, runnable)
    }

    private fun checkSettled() {
        if (!active) {
            return
        }

        if (nowMs() - lastActivityMs >= threshold.thresholdMs()) {
            active = false
            // The caller performs its state-specific completion and is responsible for cancel().
            onSettled(lastActivityMs)
        } else {
            scheduleCheck()
        }
    }

    private fun nowMs(): Long = SystemClock.uptimeMillis()

    /**
     * Supplies the idle threshold (in millis) for a [SettleTracker]. There is no guarantee about when this is called, but the
     * [SettleCallback] is only called once at least [thresholdMs] has passed since [SettleTracker.lastActivityMs] on
     * [SystemClock.uptimeMillis].
     */
    internal fun interface ThresholdProvider {
        fun thresholdMs(): Long
    }

    /**
     * Notified when the UI settles, with the activity baseline (in millis) it settled at.
     */
    internal fun interface SettleCallback {
        operator fun invoke(lastActivityMs: Long)
    }
}
