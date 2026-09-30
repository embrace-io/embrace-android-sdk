package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved vitals (smoothness / screen-load) config.
 */
interface VitalsConfig {

    /**
     * How long a smoothness focal moment must be idle (no redraw, no touch) before it is considered settled.
     */
    val smoothnessIdleThresholdMs: Long

    /**
     * The "press and hold" settle threshold used when no move event arrives within the idle threshold.
     */
    val smoothnessHeldIdleThresholdMs: Long

    /**
     * The grace multiplier applied to a frame's budget/deadline before it is considered janky.
     */
    val jankHeuristicMultiplier: Double

    /**
     * How long a screen-load destination must be idle before it is considered settled.
     */
    val screenLoadIdleThresholdMs: Long

    /**
     * Maximum time a screen-load is allowed to run before being force-reported as timed out.
     */
    val screenLoadTimeoutMs: Long

    /**
     * Maximum time between a tap and the navigation start event for the two to be linked as one screen load.
     */
    val screenLoadNavTimeoutMs: Long

    /**
     * Whether this device is sampled-in to record a per-frame duration trace alongside the smoothness result,
     * as a diagnostic aid while smoothness thresholds are being tuned.
     */
    val smoothnessFrameTraceEnabled: Boolean

    /**
     * The maximum number of vitals spans (smoothness and screen-load combined) that may be emitted per session
     * part. Vitals emits a span per user gesture and per navigation, so this caps the share of the session's
     * span budget that the feature can consume.
     */
    val spanLimit: Int

    companion object {
        const val DEFAULT_SMOOTHNESS_IDLE_THRESHOLD_MS: Long = 100L
        const val DEFAULT_SMOOTHNESS_HELD_IDLE_THRESHOLD_MS: Long = 500L
        const val DEFAULT_JANK_HEURISTIC_MULTIPLIER: Double = 2.0
        const val DEFAULT_SCREEN_LOAD_IDLE_THRESHOLD_MS: Long = 1000L
        const val DEFAULT_SCREEN_LOAD_TIMEOUT_MS: Long = 30_000L
        const val DEFAULT_SCREEN_LOAD_NAV_TIMEOUT_MS: Long = 500L
        const val DEFAULT_SPAN_LIMIT: Int = 250
    }
}

/**
 * Creates a [VitalsConfig]. A provider returning null means the default is used.
 */
inline fun VitalsConfig(
    crossinline smoothnessIdleThresholdMs: () -> Long? = { null },
    crossinline smoothnessHeldIdleThresholdMs: () -> Long? = { null },
    crossinline jankHeuristicMultiplier: () -> Double? = { null },
    crossinline screenLoadIdleThresholdMs: () -> Long? = { null },
    crossinline screenLoadTimeoutMs: () -> Long? = { null },
    crossinline screenLoadNavTimeoutMs: () -> Long? = { null },
    crossinline smoothnessFrameTraceEnabled: () -> Boolean? = { null },
    crossinline spanLimit: () -> Int? = { null },
): VitalsConfig = object : VitalsConfig {
    override val smoothnessIdleThresholdMs: Long =
        smoothnessIdleThresholdMs() ?: VitalsConfig.DEFAULT_SMOOTHNESS_IDLE_THRESHOLD_MS
    override val smoothnessHeldIdleThresholdMs: Long =
        smoothnessHeldIdleThresholdMs() ?: VitalsConfig.DEFAULT_SMOOTHNESS_HELD_IDLE_THRESHOLD_MS
    override val jankHeuristicMultiplier: Double =
        jankHeuristicMultiplier() ?: VitalsConfig.DEFAULT_JANK_HEURISTIC_MULTIPLIER
    override val screenLoadIdleThresholdMs: Long =
        screenLoadIdleThresholdMs() ?: VitalsConfig.DEFAULT_SCREEN_LOAD_IDLE_THRESHOLD_MS
    override val screenLoadTimeoutMs: Long = screenLoadTimeoutMs() ?: VitalsConfig.DEFAULT_SCREEN_LOAD_TIMEOUT_MS
    override val screenLoadNavTimeoutMs: Long =
        screenLoadNavTimeoutMs() ?: VitalsConfig.DEFAULT_SCREEN_LOAD_NAV_TIMEOUT_MS
    override val smoothnessFrameTraceEnabled: Boolean = smoothnessFrameTraceEnabled() ?: false
    override val spanLimit: Int = spanLimit() ?: VitalsConfig.DEFAULT_SPAN_LIMIT
}
