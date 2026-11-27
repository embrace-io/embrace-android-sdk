package io.embrace.android.embracesdk.internal.vitals

import android.view.FrameMetrics
import android.view.Window
import kotlin.math.max

/**
 * The `stalledThroughNanos` before a window's first stuck frame, earlier than any frame's start.
 */
private const val NO_STALL_NANOS: Long = Long.MIN_VALUE

/**
 * Forwards each frame's `(vsyncNanos, jankNanos)` to the [FocalInteractionCallbacks], on the Vitals handler thread.
 * Version-specific extraction is delegated to [frameMetricsStrategy].
 */
internal class VitalsFrameMetricsListener(
    private val focalCallbacks: FocalInteractionCallbacks,
    private val frameMetricsStrategy: FrameMetricsStrategy,
    private val errors: OnceUiCallbackErrorTracker,
) : Window.OnFrameMetricsAvailableListener {

    // The wall time this window's stuck frames already counted, so overlapping stuck frames don't count it twice. Frames that
    // presented on time during a stall don't move it.
    private var stalledThroughNanos = NO_STALL_NANOS

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        try {
            val vsyncNanos = frameMetricsStrategy.vsyncNanos(frameMetrics)
            val frameDispatchNanos = frameMetricsStrategy.frameDispatchNanos(frameMetrics)
            val totalNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val budgetNanos = frameMetricsStrategy.jankBudgetNanos(frameMetrics)
            var jankNanos = 0L
            var expectedFrames = 1
            // the start is only read for an over-budget frame; jank and vsyncs before stalledThroughNanos are already counted
            if (totalNanos > budgetNanos) {
                val startNanos = frameMetricsStrategy.frameStartNanos(frameMetrics)
                val endNanos = startNanos + totalNanos
                jankNanos = (endNanos - max(startNanos + budgetNanos, stalledThroughNanos)).coerceAtLeast(0L)
                if (jankNanos > 0L) {
                    val intervalNanos = frameMetricsStrategy.frameIntervalNanos(frameMetrics)
                    val countedFromNanos = max(startNanos, stalledThroughNanos)
                    if (intervalNanos > 0L) {
                        expectedFrames = ((endNanos - countedFromNanos + intervalNanos - 1) / intervalNanos).toInt().coerceAtLeast(1)
                    }
                    stalledThroughNanos = countedFromNanos + expectedFrames * intervalNanos
                }
            }
            focalCallbacks.onFrame(vsyncNanos, frameDispatchNanos, jankNanos, expectedFrames)
        } catch (exc: Throwable) {
            errors.track(exc)
        }
    }
}
