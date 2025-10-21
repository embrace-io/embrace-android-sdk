package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that the ANR feature should follow.
 */
class AnrBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : AnrBehavior {

    private companion object {
        private const val DEFAULT_ANR_PCT_ENABLED = true
        private const val DEFAULT_ANR_INTERVAL_MS: Long = 100
        private const val DEFAULT_ANR_MAX_PER_INTERVAL = 80
        private const val DEFAULT_STACKTRACE_FRAME_LIMIT = DEFAULT_STACKTRACE_SIZE_LIMIT
        private const val DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION = 5
        private const val DEFAULT_ANR_MIN_CAPTURE_DURATION = 1000
    }

    override val local = local.enabledFeatures
    override val remote = remote?.anrConfig

    override fun isAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled)
            ?: DEFAULT_ANR_PCT_ENABLED
    }

    override fun getSamplingIntervalMs(): Long = remote?.sampleIntervalMs ?: DEFAULT_ANR_INTERVAL_MS

    override fun getMaxStacktracesPerInterval(): Int =
        remote?.maxStacktracesPerInterval ?: DEFAULT_ANR_MAX_PER_INTERVAL

    override fun getStacktraceFrameLimit(): Int =
        remote?.stacktraceFrameLimit ?: DEFAULT_STACKTRACE_FRAME_LIMIT

    override fun getMaxAnrIntervalsPerSession(): Int =
        remote?.anrPerSession ?: DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION

    override fun getMinDuration(): Int = remote?.minDuration ?: DEFAULT_ANR_MIN_CAPTURE_DURATION
}
