package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread

/**
 * Extracts `(vsyncNanos, frameDispatchNanos, jankNanos)` from a [FrameMetrics]. The concrete strategy is
 * selected once by API level via [create]. `vsyncNanos` is in Choreographer's `frameTimeNanos` base — when
 * the frame became visible; `frameDispatchNanos` is when its render work was dispatched, in the same base;
 * `jankNanos` is how far the frame ran past its render budget, or 0 if it met it.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal interface FrameMetricsStrategy {

    @WorkerThread
    fun vsyncNanos(frameMetrics: FrameMetrics): Long

    @WorkerThread
    fun jankNanos(frameMetrics: FrameMetrics): Long

    /**
     * When the rendering engine first dispatched work to build the frame, in the same base as
     * [vsyncNanos]: `vsyncNanos - (FrameMetrics.TOTAL_DURATION)`.
     */
    @WorkerThread
    fun frameDispatchNanos(frameMetrics: FrameMetrics): Long =
        vsyncNanos(frameMetrics) - frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)

    /**
     * Updates the display refresh interval used as the frame budget for devices that don't include the [FrameMetrics.DEADLINE]
     */
    @WorkerThread
    fun onRefreshIntervalChanged(intervalNanos: Long) {
    }

    companion object {
        /**
         * A frame counts as janky once it runs this many times past its deadline; matches JankStats' default.
         */
        const val DEFAULT_JANK_HEURISTIC_MULTIPLIER = 2.0

        /**
         * Create a [FrameMetricsStrategy] with a given [refreshIntervalNanos] and [jankHeuristicMultiplier].
         *
         * @param refreshIntervalNanos nanoseconds between frames at the current refresh rate (for devices < SDK_31)
         * @param jankHeuristicMultiplier the grace multiplier to apply to the refresh rate before a delayed frame is considered dropped
         */
        @RequiresApi(Build.VERSION_CODES.N)
        fun create(
            refreshIntervalNanos: Long,
            jankHeuristicMultiplier: Double = DEFAULT_JANK_HEURISTIC_MULTIPLIER,
        ): FrameMetricsStrategy = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                Api31FrameMetricsStrategy(jankHeuristicMultiplier)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                Api26FrameMetricsStrategy(refreshIntervalNanos, jankHeuristicMultiplier)

            else ->
                LegacyFrameMetricsStrategy(refreshIntervalNanos, jankHeuristicMultiplier)
        }
    }
}

private const val NANOS_PER_MS = 1_000_000L

/**
 * True if this is the first frame a window drew after becoming visible.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun FrameMetrics.isFirstDrawFrame(): Boolean =
    getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L

/**
 * API 31+: reads `VSYNC_TIMESTAMP` for the vsync time and the per-frame `DEADLINE`, scaled by
 * [jankHeuristicMultiplier], for the budget. The refresh interval is unused — `DEADLINE` is already
 * variable-refresh-rate aware.
 */
@RequiresApi(Build.VERSION_CODES.S)
private class Api31FrameMetricsStrategy(private val jankHeuristicMultiplier: Double) : FrameMetricsStrategy {
    override fun vsyncNanos(frameMetrics: FrameMetrics): Long =
        frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)

    override fun jankNanos(frameMetrics: FrameMetrics): Long {
        if (frameMetrics.isFirstDrawFrame()) {
            return 0L
        }
        val total = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val budget = (frameMetrics.getMetric(FrameMetrics.DEADLINE) * jankHeuristicMultiplier).toLong()
        return (total - budget).coerceAtLeast(0L)
    }
}

/**
 * API 26-30: reads `VSYNC_TIMESTAMP` for the vsync time and budgets the frame against the current
 * refresh interval, scaled by [jankHeuristicMultiplier].
 */
@RequiresApi(Build.VERSION_CODES.O)
private class Api26FrameMetricsStrategy(
    private var refreshIntervalNanos: Long,
    private val jankHeuristicMultiplier: Double,
) : FrameMetricsStrategy {
    override fun vsyncNanos(frameMetrics: FrameMetrics): Long =
        frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)

    override fun jankNanos(frameMetrics: FrameMetrics): Long {
        if (frameMetrics.isFirstDrawFrame()) {
            return 0L
        }
        val budget = (refreshIntervalNanos * jankHeuristicMultiplier).toLong()
        return (frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) - budget).coerceAtLeast(0L)
    }

    override fun onRefreshIntervalChanged(intervalNanos: Long) {
        refreshIntervalNanos = intervalNanos
    }
}

/**
 * API 24-25: reports the metrics-delivery time ([SystemClock.uptimeMillis], in nanoseconds) as the
 * vsync time, and budgets the frame against the current refresh interval, scaled by
 * [jankHeuristicMultiplier]. First-draw frames are not distinguished here, as `FIRST_DRAW_FRAME` is
 * unavailable.
 */
@RequiresApi(Build.VERSION_CODES.N)
private class LegacyFrameMetricsStrategy(
    private var refreshIntervalNanos: Long,
    private val jankHeuristicMultiplier: Double,
) : FrameMetricsStrategy {
    override fun vsyncNanos(frameMetrics: FrameMetrics): Long =
        SystemClock.uptimeMillis() * NANOS_PER_MS

    override fun jankNanos(frameMetrics: FrameMetrics): Long {
        val budget = (refreshIntervalNanos * jankHeuristicMultiplier).toLong()
        return (frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION) - budget).coerceAtLeast(0L)
    }

    override fun onRefreshIntervalChanged(intervalNanos: Long) {
        refreshIntervalNanos = intervalNanos
    }
}
