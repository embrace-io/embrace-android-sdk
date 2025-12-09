package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that the thread blockage feature should follow.
 */
class ThreadBlockageBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    remote: RemoteConfig?,
) : ThreadBlockageBehavior {

    private companion object {
        private const val DEFAULT_PCT_ENABLED = true
        private const val DEFAULT_INTERVAL_MS: Long = 100
        private const val DEFAULT_MAX_PER_INTERVAL = 80
        private const val DEFAULT_STACKTRACE_FRAME_LIMIT = DEFAULT_STACKTRACE_SIZE_LIMIT
        private const val DEFAULT_MAX_INTERVALS_PER_SESSION = 5
        private const val DEFAULT_MIN_CAPTURE_DURATION = 1000
    }

    private val remote = remote?.threadBlockageRemoteConfig

    override fun isThreadBlockageCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled)
            ?: DEFAULT_PCT_ENABLED
    }

    override fun getSamplingIntervalMs(): Long = remote?.sampleIntervalMs ?: DEFAULT_INTERVAL_MS

    override fun getMaxStacktracesPerInterval(): Int =
        remote?.maxStacktracesPerInterval ?: DEFAULT_MAX_PER_INTERVAL

    override fun getStacktraceFrameLimit(): Int =
        remote?.stacktraceFrameLimit ?: DEFAULT_STACKTRACE_FRAME_LIMIT

    override fun getMaxIntervalsPerSession(): Int =
        remote?.intervalsPerSession ?: DEFAULT_MAX_INTERVALS_PER_SESSION

    override fun getMinDuration(): Int = remote?.minDuration ?: DEFAULT_MIN_CAPTURE_DURATION
}
