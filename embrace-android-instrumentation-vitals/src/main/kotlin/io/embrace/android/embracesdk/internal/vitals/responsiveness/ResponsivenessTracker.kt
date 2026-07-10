package io.embrace.android.embracesdk.internal.vitals.responsiveness

import android.os.Handler
import android.os.SystemClock.uptimeMillis
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.vitals.FocalInteractionCallbacks
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler

/**
 * Tracks how slowly the app responds to a tap, split into two stages: [onInteractionStart] notes the raw
 * touch-down time and hops onto the Vitals thread; [onFrame] closes the first stage once the next frame
 * draws, then posts a check to the main thread to close the second stage — how much longer the queue took
 * to drain after that frame. Everything else in [FocalInteractionCallbacks] is irrelevant to this vital
 * and is a no-op.
 */
internal class ResponsivenessTracker(
    private val scheduler: VitalsScheduler,
    private val emit: (ResponsivenessResult) -> Unit,
    private val mainThreadHandler: Handler,
    private val clock: Clock,
    private val minResponsivenessMs: Long = 250L,
) : FocalInteractionCallbacks {

    private val checkDrainRunnable = Runnable { checkDrain() }

    private enum class Stage { IDLE, AWAITING_FRAME, AWAITING_DRAIN }

    @Volatile
    private var stage = Stage.IDLE

    @Volatile
    private var tapUptimeMs = 0L

    @Volatile
    private var tapEpochMs = 0L

    @Volatile
    private var frameUptimeMs = 0L

    @MainThread
    override fun onInteractionStart(eventTime: Long) {
        scheduler.post { recordTap(eventTime) }
    }

    @WorkerThread
    override fun onFrame(vsyncNanos: Long, frameDispatchNanos: Long, jankNanos: Long) {
        if (stage != Stage.AWAITING_FRAME) {
            return
        }

        frameUptimeMs = vsyncNanos.nanosToMillis()
        stage = Stage.AWAITING_DRAIN
        mainThreadHandler.post(checkDrainRunnable)
    }

    override fun onScreenStart() {}
    override fun onScreenStop() {}
    override fun onInteractionMove() {}
    override fun onInteractionEnd() {}
    override fun onTap(eventTime: Long) {}
    override fun onNavigationStart(screenName: String?) {}
    override fun onNavigationEnd(screenName: String?) {}
    override fun onWindowFocused() {}
    override fun onAppBackgrounded() {}

    @WorkerThread
    private fun recordTap(eventTime: Long) {
        // the raw event time predates this call; back-date the epoch by the same amount so it lines up with eventTime
        val epochOffsetMs = clock.now() - uptimeMillis()
        tapUptimeMs = eventTime
        tapEpochMs = eventTime + epochOffsetMs
        stage = Stage.AWAITING_FRAME
    }

    @MainThread
    private fun checkDrain() {
        val drainUptimeMs = uptimeMillis()
        val tapToFrameMs = frameUptimeMs - tapUptimeMs
        val frameToDrainMs = drainUptimeMs - frameUptimeMs

        if (tapToFrameMs + frameToDrainMs >= minResponsivenessMs) {
            emit(ResponsivenessResult(tapEpochMs, tapToFrameMs, frameToDrainMs))
        }

        stage = Stage.IDLE
    }
}
