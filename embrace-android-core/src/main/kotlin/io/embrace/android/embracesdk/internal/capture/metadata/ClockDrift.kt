package io.embrace.android.embracesdk.internal.capture.metadata

/**
 * Reported difference / drift between our internal clock and the system auxiliary clocks (GNSS and Network Time). Positive values
 * indicate that the specified clock is behind our clock, negative values indicate it is ahead.
 */
data class ClockDrift(
    val networkDriftMillis: Long?,
    val gnssDriftMillis: Long?,
) {
    companion object {
        /**
         * Computes the drift between the wall clock and an auxiliary clock, both expressed in milliseconds since the epoch.
         * Positive return values indicate the auxiliary clock is behind the wall clock; negative values indicate it is ahead.
         */
        @JvmStatic
        fun calculateDrift(wallTimeMillis: Long, auxTimeMillis: Long): Long = wallTimeMillis - auxTimeMillis
    }
}
