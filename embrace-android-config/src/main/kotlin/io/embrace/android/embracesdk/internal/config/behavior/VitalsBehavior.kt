package io.embrace.android.embracesdk.internal.config.behavior

interface VitalsBehavior {

    /**
     * How long a smoothness focal moment must be idle (no redraw, no touch) before it is considered settled.
     */
    fun getSmoothnessIdleThresholdMs(): Long

    /**
     * The "press and hold" settle threshold used when no move event arrives within the idle threshold.
     */
    fun getSmoothnessHeldIdleThresholdMs(): Long

    /**
     * The grace multiplier applied to a frame's budget/deadline before it is considered janky.
     */
    fun getJankHeuristicMultiplier(): Double

    /**
     * How long a screen-load destination must be idle before it is considered settled.
     */
    fun getScreenLoadIdleThresholdMs(): Long

    /**
     * Maximum time a screen-load is allowed to run before being force-reported as timed out.
     */
    fun getScreenLoadTimeoutMs(): Long

    /**
     * Maximum time between a tap and the navigation start event for the two to be linked as one screen load.
     */
    fun getScreenLoadNavTimeoutMs(): Long

    /**
     * Whether this device is sampled-in to record a per-frame duration trace alongside the smoothness result,
     * as a diagnostic aid while smoothness thresholds are being tuned.
     */
    fun isSmoothnessFrameTraceEnabled(): Boolean
}
