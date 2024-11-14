package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder

class FakeAnrBehavior(
    var nativeThreadAnrSamplingEnabled: Boolean = false,
    var anrCaptureEnabled: Boolean = true,
    var bgAnrCaptureEnabled: Boolean = false,
    var nativeThreadAnrSamplingAllowlistIgnored: Boolean = true,
    var sampleIntervalMsImpl: Long = 5,
    var anrPerSessionImpl: Int = 5,
    var frameLimit: Int = 200,
    var nativeThreadAnrSamplingAllowlistImpl: List<AllowedNdkSampleMethod> = emptyList(),
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
    override fun getNativeThreadAnrSamplingFactor(): Int = 10
    override fun getNativeThreadAnrSamplingUnwinder(): Unwinder = Unwinder.LIBUNWIND
    override fun isUnityAnrCaptureEnabled(): Boolean = nativeThreadAnrSamplingEnabled
    override fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean = false
    override fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean = nativeThreadAnrSamplingAllowlistIgnored
    override fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod> =
        nativeThreadAnrSamplingAllowlistImpl

    override fun getNativeThreadAnrSamplingIntervalMs(): Long = 1000
}
