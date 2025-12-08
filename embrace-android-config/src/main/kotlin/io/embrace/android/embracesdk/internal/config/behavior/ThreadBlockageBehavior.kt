package io.embrace.android.embracesdk.internal.config.behavior

interface ThreadBlockageBehavior {

    /**
     * Percentage of users for which thread blockage stack trace capture is enabled.
     */
    fun isThreadBlockageCaptureEnabled(): Boolean

    /**
     * Time between stack trace captures for time intervals after the start of a thread blockage
     */
    fun getSamplingIntervalMs(): Long

    /**
     * Maximum captured stacktraces for a single thread blockage interval.
     */
    fun getMaxStacktracesPerInterval(): Int

    /**
     * Maximum number of frames to keep in thread blockage stacktrace samples
     */
    fun getStacktraceFrameLimit(): Int

    /**
     * Maximum captured thread blockage intervals for a session.
     */
    fun getMaxIntervalsPerSession(): Int

    /**
     * Minimum duration of a thread blockage interval
     */
    fun getMinDuration(): Int
}
