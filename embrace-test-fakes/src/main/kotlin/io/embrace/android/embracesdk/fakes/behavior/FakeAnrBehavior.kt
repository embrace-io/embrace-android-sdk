package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig

class FakeAnrBehavior(
    var anrCaptureEnabled: Boolean = true,
    var bgAnrCaptureEnabled: Boolean = false,
    var sampleIntervalMsImpl: Long = 5,
    var anrPerSessionImpl: Int = 5,
    var frameLimit: Int = 200,
) : AnrBehavior {

    override val local: EnabledFeatureConfig
        get() = throw UnsupportedOperationException()
    override val remote: AnrRemoteConfig
        get() = throw UnsupportedOperationException()

    override fun isAnrCaptureEnabled(): Boolean = anrCaptureEnabled
    override fun getSamplingIntervalMs(): Long = sampleIntervalMsImpl
    override fun getMaxStacktracesPerInterval(): Int = 80
    override fun getStacktraceFrameLimit(): Int = frameLimit
    override fun getMaxAnrIntervalsPerSession(): Int = anrPerSessionImpl
    override fun getMinDuration(): Int = 1000
}
