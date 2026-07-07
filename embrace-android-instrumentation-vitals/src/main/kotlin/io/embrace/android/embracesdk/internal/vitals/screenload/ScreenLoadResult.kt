package io.embrace.android.embracesdk.internal.vitals.screenload

/**
 * A completed screen-load measurement: the time from the initial interaction until the destination screen settled.
 */
internal data class ScreenLoadResult(
    /**
     * Wall-clock (epoch millis) start of the load, typically the tap that triggered the navigation event.
     */
    val startTimeMs: Long,
    /**
     * Duration from [startTimeMs] until the navigation start event that confirmed this as a screen load.
     */
    val navStartDelayMs: Long,
    /**
     * Duration from the navigation start event until the navigation end event, i.e. how long the navigation itself took.
     */
    val navDurationMs: Long,
    /**
     * Duration from the navigation end event until the first frame rendered on the destination, or 0 if no frame arrived
     * before the load completed.
     */
    val firstFrameDurationMs: Long,
    /**
     * Load duration (initial interaction to settle), in millis.
     */
    val durationMs: Long,
    /**
     * The destination screen name (last writer wins between navigation start and end).
     */
    val screenName: String,
    /**
     * How the load ended.
     */
    val outcome: ScreenLoadOutcome,
)
