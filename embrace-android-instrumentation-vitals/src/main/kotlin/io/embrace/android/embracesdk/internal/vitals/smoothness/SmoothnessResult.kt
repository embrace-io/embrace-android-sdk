package io.embrace.android.embracesdk.internal.vitals.smoothness

/**
 * The smoothness outcome of a focal moment. [normalizedDroppedFrames] is expressed in 60fps reference
 * frames, so it is refresh-rate independent (one dropped frame is 0.5 at 120Hz, 2.0 at 30Hz).
 */
internal data class SmoothnessResult(
    val outcome: FocalOutcome,
    /**
     * Wall-clock start of the focal moment (epoch millis).
     */
    val startTimeMs: Long,
    /**
     * Focal moment duration (from start to end), in millis.
     */
    val durationMs: Long,
    /**
     * Number of frames rendered over the focal moment.
     */
    val frameCount: Int,
    /**
     * Total jank over the focal moment, expressed in 60fps-reference frames.
     */
    val normalizedDroppedFrames: Double,
)
