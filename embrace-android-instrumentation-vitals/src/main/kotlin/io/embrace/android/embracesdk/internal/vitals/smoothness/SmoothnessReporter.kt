package io.embrace.android.embracesdk.internal.vitals.smoothness

import androidx.annotation.WorkerThread

/**
 * Accumulates each frame's jank over a focal moment, normalized to a 60fps reference frame, and emits a
 * [SmoothnessResult] on close. A focal moment that rendered no frames emits nothing.
 *
 * Driven directly by [FocalMomentTracker].
 */
internal class SmoothnessReporter(
    private val emit: (SmoothnessResult) -> Unit,
) {

    private var startTimeMs = 0L
    private var frameCount = 0
    private var normalizedDroppedFrames = 0.0

    @WorkerThread
    fun onFocalMomentStart(startTimeMs: Long) {
        this.startTimeMs = startTimeMs
        frameCount = 0
        normalizedDroppedFrames = 0.0
    }

    @WorkerThread
    fun onFocalMomentFrame(jankNanos: Long) {
        frameCount++
        normalizedDroppedFrames += jankNanos.toDouble() / REFERENCE_FRAME_NANOS
    }

    @WorkerThread
    fun onFocalMomentEnd(outcome: FocalOutcome, durationMs: Long) {
        if (frameCount == 0) {
            // No frames rendered: nothing to report.
            return
        }
        emit(
            SmoothnessResult(
                outcome = outcome,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                frameCount = frameCount,
                normalizedDroppedFrames = normalizedDroppedFrames,
            )
        )
    }

    private companion object {
        /**
         * A 60fps reference frame in nanoseconds; all jank is normalized to this.
         */
        private const val REFERENCE_FRAME_NANOS = 1_000_000_000.0 / 60.0
    }
}
