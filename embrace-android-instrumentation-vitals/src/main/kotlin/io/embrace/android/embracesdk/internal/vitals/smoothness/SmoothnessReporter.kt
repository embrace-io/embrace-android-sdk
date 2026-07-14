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
    private val idleThresholdMs: Long = FocalMomentTracker.DEFAULT_IDLE_THRESHOLD_MS,
    private val heldIdleThresholdMs: Long = FocalMomentTracker.DEFAULT_HELD_IDLE_THRESHOLD_MS,
    private val jankHeuristicMultiplier: Double = DEFAULT_JANK_HEURISTIC_MULTIPLIER,
) {

    private var frameCount = 0
    private var normalizedDroppedFrames = 0.0

    @WorkerThread
    fun onFocalMomentStart() {
        frameCount = 0
        normalizedDroppedFrames = 0.0
    }

    @WorkerThread
    fun onFocalMomentFrame(jankNanos: Long) {
        frameCount++
        normalizedDroppedFrames += jankNanos.toDouble() / REFERENCE_FRAME_NANOS
    }

    @WorkerThread
    fun onFocalMomentEnd(outcome: FocalOutcome, startTimeMs: Long, durationMs: Long) {
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
                idleThresholdMs = idleThresholdMs,
                heldIdleThresholdMs = heldIdleThresholdMs,
                jankHeuristicMultiplier = jankHeuristicMultiplier,
            ),
        )
    }

    internal companion object {
        /**
         * A 60fps reference frame in nanoseconds; all jank is normalized to this.
         */
        private const val REFERENCE_FRAME_NANOS = 1_000_000_000.0 / 60.0

        /**
         * A frame counts as janky once it runs this many times past its deadline; matches JankStats' default.
         */
        const val DEFAULT_JANK_HEURISTIC_MULTIPLIER = 2.0
    }
}
