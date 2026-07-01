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
