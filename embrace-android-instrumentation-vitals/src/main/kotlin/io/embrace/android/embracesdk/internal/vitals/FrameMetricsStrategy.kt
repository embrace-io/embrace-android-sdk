package io.embrace.android.embracesdk.internal.vitals

import android.os.Build
import android.os.SystemClock
import android.view.FrameMetrics
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.vitals.smoothness.SmoothnessReporter

/**
 * Extracts `(vsyncNanos, frameDispatchNanos, jankBudgetNanos)` from a [FrameMetrics]. The concrete strategy is
 * selected once by API level via [create]. `vsyncNanos` is in Choreographer's `frameTimeNanos` base — when
 * the frame became visible; `frameDispatchNanos` is when its render work was dispatched, in the same base;
 * `jankBudgetNanos` is how long the frame may render before it counts as jank.
 */
internal interface FrameMetricsStrategy {

    @WorkerThread
    fun vsyncNanos(frameMetrics: FrameMetrics): Long

    /**
     * The frame's intended vsync, which `TOTAL_DURATION` is measured from, in the same base as [vsyncNanos].
     */
    @WorkerThread
    fun frameStartNanos(frameMetrics: FrameMetrics): Long

    /**
     * How long the frame may render from [frameStartNanos] before it counts as jank, or [Long.MAX_VALUE] if it can't jank.
     */
    @WorkerThread
    fun jankBudgetNanos(frameMetrics: FrameMetrics): Long

    /**
     * When the rendering engine first dispatched work to build the frame, in the same base as
     * [vsyncNanos]: `vsyncNanos - (FrameMetrics.TOTAL_DURATION)`.
     */
    @WorkerThread
    fun frameDispatchNanos(frameMetrics: FrameMetrics): Long =
        vsyncNanos(frameMetrics) - frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)

    /**
     * The frame interval (without the jank heuristic multiplier) the frame was expected to render within.
     */
    @WorkerThread
    fun frameIntervalNanos(frameMetrics: FrameMetrics): Long

    /**
     * Updates the display refresh interval that frames are budgeted and counted against.
     */
    @WorkerThread
    fun onRefreshIntervalChanged(intervalNanos: Long) {
    }

    companion object {
        /**
         * Create a [FrameMetricsStrategy] with a given [refreshIntervalNanos] and [jankHeuristicMultiplier].
         *
         * @param refreshIntervalNanos nanoseconds between frames at the current refresh rate
         * @param jankHeuristicMultiplier the grace multiplier to apply to the refresh rate before a delayed frame is considered dropped
         * @param versionChecker selects the strategy for the device's API level
         */
        fun create(
            refreshIntervalNanos: Long,
            jankHeuristicMultiplier: Double = SmoothnessReporter.DEFAULT_JANK_HEURISTIC_MULTIPLIER,
            versionChecker: VersionChecker = BuildVersionChecker,
        ): FrameMetricsStrategy = when {
            versionChecker.isAtLeast(Build.VERSION_CODES.O) ->
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
 * API 26+: reads `VSYNC_TIMESTAMP` for the vsync time and budgets the frame against the current
 * refresh interval, scaled by [jankHeuristicMultiplier].
 */
@RequiresApi(Build.VERSION_CODES.O)
private class Api26FrameMetricsStrategy(
    private var refreshIntervalNanos: Long,
    private val jankHeuristicMultiplier: Double,
) : FrameMetricsStrategy {
    override fun vsyncNanos(frameMetrics: FrameMetrics): Long =
        frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)

    override fun frameStartNanos(frameMetrics: FrameMetrics): Long =
        frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)

    override fun jankBudgetNanos(frameMetrics: FrameMetrics): Long {
        if (frameMetrics.isFirstDrawFrame()) {
            return Long.MAX_VALUE
        }
        return (refreshIntervalNanos * jankHeuristicMultiplier).toLong()
    }

    override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = refreshIntervalNanos

    override fun onRefreshIntervalChanged(intervalNanos: Long) {
        refreshIntervalNanos = intervalNanos
    }
}

/**
 * API 24-25: reports the metrics-delivery time ([SystemClock.uptimeMillis], in nanoseconds) as the
 * vsync time, and budgets the frame against the current refresh interval, scaled by
 * [jankHeuristicMultiplier]. First-draw frames are not distinguished here, as `FIRST_DRAW_FRAME` is
 * unavailable, and the frame's start is approximated by the delivery time less its duration.
 */
private class LegacyFrameMetricsStrategy(
    private var refreshIntervalNanos: Long,
    private val jankHeuristicMultiplier: Double,
) : FrameMetricsStrategy {
    override fun vsyncNanos(frameMetrics: FrameMetrics): Long = SystemClock.uptimeMillis() * NANOS_PER_MS

    override fun frameStartNanos(frameMetrics: FrameMetrics): Long = frameDispatchNanos(frameMetrics)

    override fun jankBudgetNanos(frameMetrics: FrameMetrics): Long = (refreshIntervalNanos * jankHeuristicMultiplier).toLong()

    override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = refreshIntervalNanos

    override fun onRefreshIntervalChanged(intervalNanos: Long) {
        refreshIntervalNanos = intervalNanos
    }
}
