package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.vitals.FocalInteractionCallbacks
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler

/**
 * Tracks the smoothness of a focal moment — framerate quality while the user is actively engaged. A
 * focal moment opens on a touch-down and ends when the interaction goes quiet (no redraw, no touch) for
 * the idle threshold; each frame's jank is fed to [reporter], which emits a [SmoothnessResult] on close.
 *
 * The whole state machine runs on the Vitals handler thread: [onFrame] is delivered there, the settle
 * timeout fires there, and the main-thread touch/screen callbacks hop onto it via `scheduler.post`, so
 * the state needs no synchronization.
 */
internal class FocalMomentTracker(
    private val scheduler: VitalsScheduler,
    private val reporter: SmoothnessReporter,
    private val clock: Clock,
    private val idleThresholdNanos: Long = IDLE_THRESHOLD_NANOS,
    private val heldIdleThresholdNanos: Long = HELD_IDLE_THRESHOLD_NANOS,
) : FocalInteractionCallbacks {

    // Reused hop Runnable instances (main thread -> Vitals thread); allocate them here to make certain they're off the hot path
    private val interactionRunnable = Runnable { startOrResume() }
    private val interactionEndRunnable = Runnable { handleInteractionEnd() }
    private val screenChangeRunnable = Runnable { handleScreenChange() }
    private val settleRunnable = Runnable { settleCheck() }

    private var capturing = false
    private var held = false
    private var focalMomentStartNanos = 0L
    private var lastRedrawVsyncNanos = 0L
    private var interacting = false
    private var lastMoveNanos = 0L
    private var bufferedEndNanos = 0L

    @MainThread
    override fun onInteractionStart() = scheduler.post(interactionRunnable)

    @MainThread
    override fun onInteractionMove() = scheduler.post(interactionRunnable)

    @MainThread
    override fun onInteractionEnd() = scheduler.post(interactionEndRunnable)

    @MainThread
    override fun onScreenStart() = scheduler.post(screenChangeRunnable)

    @MainThread
    override fun onScreenStop() = scheduler.post(screenChangeRunnable)

    @WorkerThread
    override fun onFrame(vsyncNanos: Long, jankNanos: Long) {
        if (capturing) {
            recordFrame(vsyncNanos, jankNanos)
            armSettle()
        } else if (held) {
            if (vsyncNanos - bufferedEndNanos < idleThresholdNanos) {
                // Frame is contiguous with the buffered settle: resume capturing so its jank is included.
                held = false
                capturing = true
                recordFrame(vsyncNanos, jankNanos)
                armSettle()
            } else {
                // Idle gap before this frame: emit the buffered settle and discard the orphan frame.
                flushHeld()
            }
        }
    }

    @WorkerThread
    private fun startOrResume() {
        if (held) {
            // Emit the buffered result, then open a fresh interaction.
            flushHeld()
            startFocalMoment()
        } else if (capturing) {
            interacting = true
            lastMoveNanos = nowNanos()
            armSettle()
        } else {
            startFocalMoment()
        }
    }

    @WorkerThread
    private fun handleInteractionEnd() {
        interacting = false
        if (capturing) {
            armSettle()
        }
    }

    @WorkerThread
    private fun handleScreenChange() {
        // Flush a buffered settle or interrupt an open focal moment. Startup opens nothing.
        if (held) {
            flushHeld()
        } else if (capturing) {
            completeFocalMoment(FocalOutcome.INTERRUPTED, endNanos = nowNanos())
        }
    }

    @WorkerThread
    private fun startFocalMoment() {
        val now = nowNanos()
        focalMomentStartNanos = now
        lastRedrawVsyncNanos = now
        held = false
        interacting = true
        lastMoveNanos = now
        capturing = true
        reporter.onFocalMomentStart(clock.now())
        armSettle()
    }

    @WorkerThread
    private fun recordFrame(vsyncNanos: Long, jankNanos: Long) {
        if (vsyncNanos > lastRedrawVsyncNanos) {
            lastRedrawVsyncNanos = vsyncNanos
        }
        reporter.onFocalMomentFrame(jankNanos)
    }

    /**
     * (Re)schedules the settle check for the current activity baseline plus its threshold.
     */
    @WorkerThread
    private fun armSettle() {
        val delayNanos = (lastActivityNanos() + currentThresholdNanos() - nowNanos()).coerceAtLeast(0L)
        scheduler.scheduleSettle(delayNanos / NANOS_PER_MS, settleRunnable)
    }

    /**
     * Fired by the scheduler: settle if the interaction is quiet enough, else re-arm for the remainder.
     */
    @WorkerThread
    private fun settleCheck() {
        if (!capturing) {
            return
        }
        val lastActivity = lastActivityNanos()
        if (nowNanos() - lastActivity >= currentThresholdNanos()) {
            enterHeld(endNanos = lastActivity)
        } else {
            armSettle()
        }
    }

    @WorkerThread
    private fun lastActivityNanos(): Long = maxOf(lastRedrawVsyncNanos, lastMoveNanos)

    @WorkerThread
    private fun currentThresholdNanos(): Long = if (interacting) heldIdleThresholdNanos else idleThresholdNanos

    /**
     * Emits a buffered settled result if one is pending.
     */
    @WorkerThread
    private fun flushHeld() {
        if (held) {
            completeFocalMoment(FocalOutcome.SETTLED, bufferedEndNanos)
        }
    }

    @WorkerThread
    private fun completeFocalMoment(outcome: FocalOutcome, endNanos: Long) {
        if (!capturing && !held) {
            return
        }
        capturing = false
        held = false
        scheduler.cancelSettle()
        val durationMs = (endNanos - focalMomentStartNanos) / NANOS_PER_MS
        reporter.onFocalMomentEnd(outcome, durationMs)
    }

    /** Buffers a tentative settle and awaits the emit event. */
    @WorkerThread
    private fun enterHeld(endNanos: Long) {
        bufferedEndNanos = endNanos
        capturing = false
        held = true
        scheduler.cancelSettle()
    }

    private fun nowNanos(): Long = SystemClock.uptimeMillis() * NANOS_PER_MS

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val IDLE_THRESHOLD_NANOS = 100L * 1_000_000L

        /**
         * The "press and hold" threshold, if we don't get a "move" event (which we really should) within this time we consider the
         * interaction / focal moment settled.
         */
        const val HELD_IDLE_THRESHOLD_NANOS = 500L * 1_000_000L
    }
}
