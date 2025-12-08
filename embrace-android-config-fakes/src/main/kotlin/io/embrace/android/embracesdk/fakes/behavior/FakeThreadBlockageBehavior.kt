package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehavior

class FakeThreadBlockageBehavior(
    var captureEnabled: Boolean = true,
    var sampleIntervalMsImpl: Long = 5,
    var intervalsPerSessionImpl: Int = 5,
    var frameLimit: Int = DEFAULT_STACKTRACE_SIZE_LIMIT,
) : ThreadBlockageBehavior {

    override fun isThreadBlockageCaptureEnabled(): Boolean = captureEnabled
    override fun getSamplingIntervalMs(): Long = sampleIntervalMsImpl
    override fun getMaxStacktracesPerInterval(): Int = 80
    override fun getStacktraceFrameLimit(): Int = frameLimit
    override fun getMaxIntervalsPerSession(): Int = intervalsPerSessionImpl
    override fun getMinDuration(): Int = 1000
}
