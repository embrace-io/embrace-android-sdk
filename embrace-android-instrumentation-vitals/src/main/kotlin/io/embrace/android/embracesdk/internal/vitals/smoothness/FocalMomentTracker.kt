package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.arch.vitals.FocalMomentType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import io.embrace.android.embracesdk.internal.vitals.FocalInteractionCallbacks
import io.embrace.android.embracesdk.internal.vitals.MainThreadProbe
import io.embrace.android.embracesdk.internal.vitals.SettleTracker
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler
import io.embrace.android.embracesdk.internal.vitals.responsiveness.InteractionResponsivenessReporter
import io.embrace.android.embracesdk.internal.vitals.screenload.ScreenLoadTracker

/**
 * Tracks the smoothness of a focal moment — framerate quality while the user is actively engaged. A
 * focal moment opens on a touch-down, or on a type emitted on [FocalMomentType.KEY], and ends when it goes
 * quiet (no redraw, no touch) for the idle threshold, or is interrupted by backgrounding or a second screen
 * load; each frame's jank is fed to [reporter], which emits a [SmoothnessResult] on close.
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
    private val idleThresholdMs: Long = DEFAULT_IDLE_THRESHOLD_MS,
    private val heldIdleThresholdMs: Long = DEFAULT_HELD_IDLE_THRESHOLD_MS,
    // Only non-null when interaction responsiveness is captured; called on the Vitals thread.
    private val responsivenessReporter: InteractionResponsivenessReporter? = null,
    // Only non-null when interaction responsiveness is captured; builds the probe that reports main-thread blocks.
    probeFactory: ((MainThreadProbe.BlockCallback) -> MainThreadProbe)? = null,
    // Receives every frame, whatever the smoothness state; called on the Vitals thread.
    private val onFrameCounted: (dropped: Boolean, expectedFrames: Int) -> Unit = { _, _ -> },
) : FocalInteractionCallbacks, EventHandler<FocalMomentType>, AutoCloseable {

    // Reused hop Runnable instances (main thread -> Vitals thread); allocate them here to make certain they're off the hot path
    private val interactionStartRunnable = Runnable { startOrResume(openedByDown = true) }
    private val interactionMoveRunnable = Runnable { startOrResume(openedByDown = false) }
    private val interactionEndRunnable = Runnable { handleInteractionEnd() }
    private val interactionTriggerRunnable = Runnable { handleTrigger(FocalMomentType.INTERACTION) }
    private val screenLoadTriggerRunnable = Runnable { handleTrigger(FocalMomentType.SCREEN_LOAD) }
    private val backgroundRunnable = Runnable { handleBackground() }
    private val closeRunnable = Runnable { handleClose() }

    // The settle baseline (the latest of redraw vsync / move) lives inside [settle]; we feed every activity
    // event to it via notifyActivity rather than holding the per-source timestamps here.
    private val settle = SettleTracker(scheduler, ::currentThresholdMs, ::onSettled)

    // Runs only while a focal moment is capturing; each block is reported on the main thread and hops onto the Vitals thread.
    private val probe: MainThreadProbe? = probeFactory?.invoke(
        MainThreadProbe.BlockCallback { endUptimeMs, durationMs -> scheduler.post { handleBlock(endUptimeMs, durationMs) } },
    )

    private var capturing = false
    private var held = false
    private var type = FocalMomentType.INTERACTION
    private var focalMomentStartNanos = 0L
    private var focalMomentStartEpochMs = 0L
    private var interacting = false
    private var bufferedEndNanos = 0L
    private var closed = false

    // The latest touch-down, written on the main thread only when responsiveness is captured.
    @Volatile
    private var pendingDownEventMs = 0L

    @Volatile
    private var pendingDownProcessedMs = 0L

    // The earlier of the opening touch-down's event and processing times, or NO_DOWN_EVENT if a move opened the moment.
    private var downStartMs = NO_DOWN_EVENT

    @WorkerThread
    override fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long, expectedFrames: Int) {
        if (capturing) {
            recordFrame(frameDispatchNanos, jankNanos)
            settle.notifyActivity(vsyncNanos.nanosToMillis())
        } else if (held) {
            if (vsyncNanos - bufferedEndNanos < idleThresholdMs.millisToNanos()) {
                // Frame is contiguous with the buffered settle: resume capturing so its jank is included.
                held = false
                capturing = true
                startProbe()
                recordFrame(frameDispatchNanos, jankNanos)
                settle.notifyActivity(vsyncNanos.nanosToMillis())
            } else {
                // Idle gap before this frame: emit the buffered settle and discard the orphan frame.
                flushHeld()
            }
        }

        // the screen load and the frame counts track their own frames, independent of the smoothness state
        screenLoadTracker.onFrame(vsyncNanos)
        onFrameCounted(jankNanos > 0L, expectedFrames)
    }

    @WorkerThread
    private fun startOrResume(openedByDown: Boolean) {
        if (held) {
            // Emit the buffered result, then open a fresh interaction.
            flushHeld()
            startFocalMoment(FocalMomentType.INTERACTION, openedByDown = openedByDown, interacting = true)
        } else if (capturing) {
            interacting = true
            settle.notifyActivity(nowMs())
        } else {
            startFocalMoment(FocalMomentType.INTERACTION, openedByDown = openedByDown, interacting = true)
        }
    }

    /**
     * A screen load turns an open interaction into a screen load, and interrupts an open screen load to start another. Otherwise a
     * trigger is activity in the open moment, or starts one if none is open. A trigger is a single event rather than a held finger, so
     * the moment it starts settles on the idle threshold.
     */
    @WorkerThread
    private fun handleTrigger(trigger: FocalMomentType) {
        if (capturing && trigger == FocalMomentType.SCREEN_LOAD && type == FocalMomentType.SCREEN_LOAD) {
            interrupt()
        }

        if (capturing) {
            if (trigger == FocalMomentType.SCREEN_LOAD) {
                type = FocalMomentType.SCREEN_LOAD
            }
            settle.notifyActivity(nowMs())
        } else {
            flushHeld()
            startFocalMoment(trigger, openedByDown = false, interacting = false)
        }
    }

    @MainThread
    override fun onInteractionStart(eventTime: Long) {
        if (responsivenessReporter != null) {
            pendingDownEventMs = eventTime
            pendingDownProcessedMs = nowMs()
        }
        scheduler.post(interactionStartRunnable)
    }

    @MainThread
    override fun onInteractionMove() =
        scheduler.post(interactionMoveRunnable)

    @MainThread
    override fun onInteractionEnd() =
        scheduler.post(interactionEndRunnable)

    /**
     * A focal moment of type [event] was triggered, see [FocalMomentType.KEY].
     */
    @AnyThread
    override fun onEvent(event: FocalMomentType) = scheduler.post(
        when (event) {
            FocalMomentType.INTERACTION -> interactionTriggerRunnable
            FocalMomentType.SCREEN_LOAD -> screenLoadTriggerRunnable
        },
    )

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
        scheduler.post(backgroundRunnable)

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
    private fun handleBackground() {
        screenLoadTracker.onInterrupt()
        interrupt()
    }

    /**
     * Flushes a buffered settle, or interrupts an open focal moment.
     */
    @WorkerThread
    private fun interrupt() {
        if (held) {
            flushHeld()
        } else if (capturing) {
            // end at the last activity, not now, so the idle gap before the interruption isn't counted
            completeFocalMoment(FocalOutcome.INTERRUPTED, endNanos = settle.lastActivityMs.millisToNanos())
        }
    }

    @WorkerThread
    private fun startFocalMoment(type: FocalMomentType, openedByDown: Boolean, interacting: Boolean) {
        val nowMs = nowMs()
        focalMomentStartNanos = nowMs.millisToNanos()
        focalMomentStartEpochMs = clock.now()
        this.type = type
        held = false
        this.interacting = interacting
        capturing = true
        reporter.onFocalMomentStart()
        if (responsivenessReporter != null) {
            if (openedByDown) {
                val eventMs = pendingDownEventMs
                val processedMs = pendingDownProcessedMs
                downStartMs = minOf(eventMs, processedMs)
                responsivenessReporter.onFocalMomentStart((processedMs - eventMs).coerceAtLeast(0L))
            } else {
                downStartMs = NO_DOWN_EVENT
                responsivenessReporter.onFocalMomentStart(null)
            }
        }
        // Started on the Vitals thread, not at the touch-down's processing time, so hop latency is never reported as a block.
        startProbe()
        settle.notifyActivity(nowMs)
    }

    @WorkerThread
    private fun startProbe() {
        if (!closed) {
            probe?.start()
        }
    }

    @WorkerThread
    private fun handleBlock(endUptimeMs: Long, durationMs: Long) {
        if (held) {
            // The report trailed the settle check that held the moment; the block is activity, so resume.
            held = false
            capturing = true
            startProbe()
        } else if (!capturing) {
            return
        }
        responsivenessReporter?.onBlock(durationMs)
        settle.notifyActivity(endUptimeMs)
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
        // A blocked main thread renders nothing, so wait for the probe's block report rather than settle mid-block.
        if (probe?.isOverdue(nowMs()) == true) {
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
        probe?.stop()
        val durationMs = (endNanos - focalMomentStartNanos).nanosToMillis()
        reporter.onFocalMomentEnd(outcome, focalMomentStartEpochMs, durationMs)
        if (responsivenessReporter != null) {
            val startNanos = if (downStartMs == NO_DOWN_EVENT) focalMomentStartNanos else downStartMs.millisToNanos()
            responsivenessReporter.onFocalMomentEnd(
                outcome = outcome,
                startTimeMs = focalMomentStartEpochMs + (startNanos - focalMomentStartNanos).nanosToMillis(),
                durationMs = (endNanos - startNanos).nanosToMillis(),
            )
        }
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
        probe?.stop()
    }

    /**
     * Stops the main-thread probe on the Vitals thread and keeps it stopped, so no probe message outlives the tracker.
     */
    override fun close() = scheduler.post(closeRunnable)

    @WorkerThread
    private fun handleClose() {
        closed = true
        probe?.stop()
    }

    private fun nowMs(): Long = SystemClock.uptimeMillis()

    companion object {
        const val DEFAULT_IDLE_THRESHOLD_MS = 100L

        /**
         * The "press and hold" threshold, if we don't get a "move" event (which we really should) within this time we consider the
         * interaction / focal moment settled.
         */
        const val DEFAULT_HELD_IDLE_THRESHOLD_MS = 500L

        private const val NO_DOWN_EVENT = -1L
    }
}
