package io.embrace.android.embracesdk.internal.config.behavior

interface AnrBehavior {

    /**
     * Percentage of users for which ANR stack trace capture is enabled.
     */
    fun isAnrCaptureEnabled(): Boolean

    /**
     * Time between stack trace captures for time intervals after the start of an ANR.
     */
    fun getSamplingIntervalMs(): Long

    /**
     * Maximum captured stacktraces for a single ANR interval.
     */
    fun getMaxStacktracesPerInterval(): Int

    /**
     * Maximum number of frames to keep in ANR stacktrace samples
     */
    fun getStacktraceFrameLimit(): Int

    /**
     * Maximum captured anr for a session.
     */
    fun getMaxAnrIntervalsPerSession(): Int

    /**
     * Minimum duration of an ANR interval
     */
    fun getMinDuration(): Int
}
