package io.embrace.android.embracesdk.internal.vitals.responsiveness

import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.vitals.smoothness.FocalOutcome

/**
 * Accumulates the main-thread blocks seen over a focal moment and emits an [InteractionResponsivenessResult] for every moment.
 */
internal class InteractionResponsivenessReporter(
    private val emit: (InteractionResponsivenessResult) -> Unit,
    private val blockThresholdMs: Long,
    private val probeIntervalMs: Long,
) {

    private var dispatchDelayMs: Long? = null
    private var longestBlockMs = 0L
    private var totalBlockedMs = 0L
    private var blockCount = 0

    @WorkerThread
    fun onFocalMomentStart(dispatchDelayMs: Long?) {
        this.dispatchDelayMs = dispatchDelayMs
        longestBlockMs = 0L
        totalBlockedMs = 0L
        blockCount = 0
    }

    @WorkerThread
    fun onBlock(durationMs: Long) {
        if (durationMs > longestBlockMs) {
            longestBlockMs = durationMs
        }
        totalBlockedMs += durationMs
        blockCount++
    }

    @WorkerThread
    fun onFocalMomentEnd(outcome: FocalOutcome, startTimeMs: Long, durationMs: Long) {
        emit(
            InteractionResponsivenessResult(
                outcome = outcome,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                dispatchDelayMs = dispatchDelayMs,
                longestBlockMs = longestBlockMs,
                totalBlockedMs = totalBlockedMs,
                blockCount = blockCount,
                blockThresholdMs = blockThresholdMs,
                probeIntervalMs = probeIntervalMs,
            ),
        )
    }
}
