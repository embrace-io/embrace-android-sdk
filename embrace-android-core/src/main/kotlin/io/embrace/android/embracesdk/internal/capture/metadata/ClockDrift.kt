package io.embrace.android.embracesdk.internal.capture.metadata

/**
 * Reported difference / drift between our internal clock and the system auxiliary clocks (GNSS and Network Time). Positive values
 * indicate that the specified clock is behind our clock, negative values indicate it is ahead.
 */
class ClockDrift private constructor(
    val networkDriftMillis: Long?,
    val gnssDriftMillis: Long?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClockDrift

        if (networkDriftMillis != other.networkDriftMillis) return false
        if (gnssDriftMillis != other.gnssDriftMillis) return false

        return true
    }

    override fun hashCode(): Int {
        var result = networkDriftMillis?.hashCode() ?: 0
        result = 31 * result + (gnssDriftMillis?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "ClockDrift(networkDriftMillis=$networkDriftMillis, gnssDriftMillis=$gnssDriftMillis)"
    }

    companion object {
        fun fromWallDrift(wallTimeMillis: Long, networkTimeMillis: Long?, gnssTimeMillis: Long?) =
            ClockDrift(
                networkDriftMillis = networkTimeMillis?.let { wallTimeMillis - it },
                gnssDriftMillis = gnssTimeMillis?.let { wallTimeMillis - it },
            )
    }
}
