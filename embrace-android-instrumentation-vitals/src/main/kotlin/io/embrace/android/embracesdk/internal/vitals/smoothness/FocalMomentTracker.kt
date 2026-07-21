package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.vitals.FocalInteractionCallbacks
import io.embrace.android.embracesdk.internal.vitals.SettleTracker
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler
import io.embrace.android.embracesdk.internal.vitals.screenload.ScreenLoadTracker

/**
 * Tracks the smoothness of a focal moment — framerate quality while the user is actively engaged. A
 * focal moment opens on a touch-down and ends when the interaction goes quiet (no redraw, no touch) for
 * the idle threshold; each frame's jank is fed to [reporter], which emits a [SmoothnessResult] on close.
 *
 * The whole state machine runs on the Vitals handler thread: [onFrame] is delivered there, the settle
 * timeout elapses there, and the main-thread touch/screen callbacks hop onto it via `scheduler.post`, so
 * the state needs no synchronization.
 */
internal class FocalMomentTracker(
    private val scheduler: VitalsScheduler,
    private val reporter: SmoothnessReporter,
    private val clock: Clock,
    private val screenLoadTracker: ScreenLoadTracker,
    private val idleThresholdMs: Long = IDLE_THRESHOLD_MS,
    private val heldIdleThresholdMs: Long = HELD_IDLE_THRESHOLD_MS,
) : FocalInteractionCallbacks {

    // Reused hop Runnable instances (main thread -> Vitals thread); allocate them here to make certain they're off the hot path
    private val interactionRunnable = Runnable { startOrResume() }
    private val interactionEndRunnable = Runnable { handleInteractionEnd() }
    private val screenChangeRunnable = Runnable { handleScreenChange() }

    // The settle baseline (the latest of redraw vsync / move) lives inside [settle]; we feed every activity
    // event to it via notifyActivity rather than holding the per-source timestamps here.
    private val settle = SettleTracker(scheduler, ::currentThresholdMs, ::onSettled)

    private var capturing = false
    private var held = false
    private var focalMomentStartNanos = 0L
    private var focalMomentStartEpochMs = 0L
    private var interacting = false
    private var bufferedEndNanos = 0L

    @WorkerThread
    override fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long) {
        if (capturing) {
            recordFrame(frameDispatchNanos, jankNanos)
            settle.notifyActivity(vsyncNanos.nanosToMillis())
        } else if (held) {
            if (vsyncNanos - bufferedEndNanos < idleThresholdMs.millisToNanos()) {
                // Frame is contiguous with the buffered settle: resume capturing so its jank is included.
                held = false
                capturing = true
                recordFrame(frameDispatchNanos, jankNanos)
                settle.notifyActivity(vsyncNanos.nanosToMillis())
            } else {
                // Idle gap before this frame: emit the buffered settle and discard the orphan frame.
                flushHeld()
            }
        }

        // the screen load tracks its own frames, independent of the smoothness state
        screenLoadTracker.onFrame(vsyncNanos)
    }

    @WorkerThread
    private fun startOrResume() {
        if (held) {
            // Emit the buffered result, then open a fresh interaction.
            flushHeld()
            startFocalMoment()
        } else if (capturing) {
            interacting = true
            settle.notifyActivity(nowMs())
        } else {
            startFocalMoment()
        }
    }

    @MainThread
    override fun onInteractionStart() =
        scheduler.post(interactionRunnable)

    @MainThread
    override fun onInteractionMove() =
        scheduler.post(interactionRunnable)

    @MainThread
    override fun onInteractionEnd() =
        scheduler.post(interactionEndRunnable)

    @MainThread
    override fun onScreenStart() =
        scheduler.post(screenChangeRunnable)

    @MainThread
    override fun onScreenStop() =
        scheduler.post(screenChangeRunnable)

    // screen-load signals hop onto the Vitals thread and forward to the screen-load tracker

    @MainThread
    override fun onTap(eventTime: Long) = scheduler.post { screenLoadTracker.onTap(eventTime) }

    @MainThread
    override fun onNavigationStart(screenName: String?, eventTime: Long) =
        scheduler.post { screenLoadTracker.onNavigationStart(screenName, eventTime) }

    @MainThread
    override fun onNavigationEnd(screenName: String?, eventTime: Long) =
        scheduler.post { screenLoadTracker.onNavigationEnd(screenName, eventTime) }

    @MainThread
    override fun onWindowFocused() =
        scheduler.post { screenLoadTracker.onWindowFocused() }

    @MainThread
    override fun onAppBackgrounded() =
        scheduler.post { screenLoadTracker.onInterrupt() }

    @WorkerThread
    private fun handleInteractionEnd() {
        interacting = false
        // The threshold just dropped from held to idle, moving the deadline earlier; re-post so the settle
        // runs on the shorter idle window rather than waiting out the original held one.
        if (capturing) {
            settle.reschedule()
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
        val nowMs = nowMs()
        focalMomentStartNanos = nowMs.millisToNanos()
        focalMomentStartEpochMs = clock.now()
        held = false
        interacting = true
        capturing = true
        reporter.onFocalMomentStart()
        settle.notifyActivity(nowMs)
    }

    @WorkerThread
    private fun recordFrame(frameDispatchNanos: Long, jankNanos: Long) {
        // A jank frame is counted in full, so the moment must also cover the render that produced it. We
        // back-date the start to when the engine dispatched the frame's work, if that predates the
        // moment, shifting the reported start epoch by the same amount so the end stays put.
        if (frameDispatchNanos < focalMomentStartNanos) {
            focalMomentStartEpochMs -= (focalMomentStartNanos - frameDispatchNanos).nanosToMillis()
            focalMomentStartNanos = frameDispatchNanos
        }

        reporter.onFocalMomentFrame(jankNanos)
    }

    /**
     * Invoked by [settle] when the interaction has been quiet for the current threshold: buffer the settle
     * at the last activity baseline. A late callback after the moment already closed is ignored.
     */
    @WorkerThread
    private fun onSettled(lastActivityMs: Long) {
        if (!capturing) {
            return
        }
        enterHeld(endNanos = lastActivityMs.millisToNanos())
    }

    @WorkerThread
    private fun currentThresholdMs(): Long = if (interacting) heldIdleThresholdMs else idleThresholdMs

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
        settle.cancel()
        val durationMs = (endNanos - focalMomentStartNanos).nanosToMillis()
        reporter.onFocalMomentEnd(outcome, focalMomentStartEpochMs, durationMs)
    }

    /**
     * Buffers a tentative settle and awaits the emit event.
     */
    @WorkerThread
    private fun enterHeld(endNanos: Long) {
        bufferedEndNanos = endNanos
        capturing = false
        held = true
        settle.cancel()
    }

    private fun nowMs(): Long = SystemClock.uptimeMillis()

    private fun nowNanos(): Long = nowMs().millisToNanos()

    private companion object {
        const val IDLE_THRESHOLD_MS = 100L

        /**
         * The "press and hold" threshold, if we don't get a "move" event (which we really should) within this time we consider the
         * interaction / focal moment settled.
         */
        const val HELD_IDLE_THRESHOLD_MS = 500L
    }
}
