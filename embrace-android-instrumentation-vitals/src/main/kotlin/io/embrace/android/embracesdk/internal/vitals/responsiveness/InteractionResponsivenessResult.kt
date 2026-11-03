package io.embrace.android.embracesdk.internal.vitals.responsiveness

import io.embrace.android.embracesdk.internal.vitals.smoothness.FocalOutcome

/**
 * The interaction responsiveness outcome of a focal moment.
 */
internal data class InteractionResponsivenessResult(
    /**
     * How did the Focal Moment end?
     */
    val outcome: FocalOutcome,
    /**
     * Wall-clock start (epoch millis): the opening touch-down's event time, or the focal moment start if a move opened it.
     */
    val startTimeMs: Long,
    /**
     * Duration from the start to the focal moment's end, in millis.
     */
    val durationMs: Long,
    /**
     * Delay between the opening touch-down's event time and its dispatch, in millis; null if a move opened the moment.
     */
    val dispatchDelayMs: Long?,
    /**
     * The longest wait seen by work posted to the main thread, sampled every [probeIntervalMs], in millis.
     */
    val longestBlockMs: Long,
    /**
     * The sum of every sampled wait of at least [blockThresholdMs], in millis.
     */
    val totalBlockedMs: Long,
    /**
     * The number of sampled waits of at least [blockThresholdMs]; a block and the queued work it delayed can count twice.
     */
    val blockCount: Int,
    /**
     * The minimum sampled wait that counted, in millis.
     */
    val blockThresholdMs: Long,
    /**
     * The interval at which the main thread was probed, in millis.
     */
    val probeIntervalMs: Long,
)
