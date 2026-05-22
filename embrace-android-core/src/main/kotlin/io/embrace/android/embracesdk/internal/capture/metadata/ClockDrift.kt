package io.embrace.android.embracesdk.internal.capture.metadata

/**
 * Reported difference / drift between our internal clock and the system auxiliary clocks (GNSS and Network Time). Positive values
 * indicate that the specified clock is behind our clock, negative values indicate it is ahead.
 */
data class ClockDrift(
    val networkDriftMillis: Long?,
    val gnssDriftMillis: Long?,
) {
    constructor(wallTimeMillis: Long, networkTimeMillis: Long?, gnssTimeMillis: Long?) : this(
        networkDriftMillis = networkTimeMillis?.let { wallTimeMillis - it },
        gnssDriftMillis = gnssTimeMillis?.let { wallTimeMillis - it },
    )
}
