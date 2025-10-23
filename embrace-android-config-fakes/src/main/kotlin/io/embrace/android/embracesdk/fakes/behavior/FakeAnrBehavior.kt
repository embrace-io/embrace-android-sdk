package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT

class FakeAnrBehavior(
    var anrCaptureEnabled: Boolean = true,
    var bgAnrCaptureEnabled: Boolean = false,
    var sampleIntervalMsImpl: Long = 5,
    var anrPerSessionImpl: Int = 5,
    var frameLimit: Int = DEFAULT_STACKTRACE_SIZE_LIMIT,
) : AnrBehavior {

    override fun isAnrCaptureEnabled(): Boolean = anrCaptureEnabled
    override fun getSamplingIntervalMs(): Long = sampleIntervalMsImpl
    override fun getMaxStacktracesPerInterval(): Int = 80
    override fun getStacktraceFrameLimit(): Int = frameLimit
    override fun getMaxAnrIntervalsPerSession(): Int = anrPerSessionImpl
    override fun getMinDuration(): Int = 1000
}
