package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import java.util.regex.Pattern

class FakeAnrBehavior(
    var strictModeListenerEnabled: Boolean = false,
    var nativeThreadAnrSamplingEnabled: Boolean = false,
    var idleHandlerEnabled: Boolean = false,
    var anrCaptureEnabled: Boolean = true,
    var googleAnrCaptureEnabled: Boolean = false,
    var bgAnrCaptureEnabled: Boolean = false,
    var nativeThreadAnrSamplingAllowlistIgnored: Boolean = true,
    var monitorThreadPriorityImpl: Int = 5,
    var sampleIntervalMsImpl: Long = 5,
    var anrPerSessionImpl: Int = 5,
    override val allowPatternList: List<Pattern> = emptyList(),
    override val blockPatternList: List<Pattern> = emptyList(),
    var nativeThreadAnrSamplingAllowlistImpl: List<AllowedNdkSampleMethod> = emptyList(),
) : AnrBehavior {

    override fun isSigquitCaptureEnabled(): Boolean = googleAnrCaptureEnabled
    override fun isAnrCaptureEnabled(): Boolean = anrCaptureEnabled
    override fun isAnrProcessErrorsCaptureEnabled(): Boolean = false
    override fun getMonitorThreadPriority(): Int = monitorThreadPriorityImpl
    override fun isBgAnrCaptureEnabled(): Boolean = bgAnrCaptureEnabled
    override fun getSamplingIntervalMs(): Long = sampleIntervalMsImpl
    override fun getAnrProcessErrorsIntervalMs(): Long = 100
    override fun getAnrProcessErrorsDelayMs(): Long = 0
    override fun getAnrProcessErrorsSchedulerExtraTimeAllowanceMs(): Long = 100
    override fun getMaxStacktracesPerInterval(): Int = 80
    override fun getStacktraceFrameLimit(): Int = 256
    override fun getMaxAnrIntervalsPerSession(): Int = anrPerSessionImpl
    override fun getMinThreadPriority(): Int = 6
    override fun getMinDuration(): Int = 1000
    override fun shouldCaptureMainThreadOnly(): Boolean = true
    override fun getNativeThreadAnrSamplingFactor(): Int = 10
    override fun getNativeThreadAnrSamplingUnwinder(): Unwinder = Unwinder.LIBUNWIND
    override fun isUnityAnrCaptureEnabled(): Boolean = nativeThreadAnrSamplingEnabled
    override fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean = false
    override fun isIdleHandlerEnabled(): Boolean = idleHandlerEnabled
    override fun isStrictModeListenerEnabled(): Boolean = strictModeListenerEnabled
    override fun getStrictModeViolationLimit(): Int = 10
    override fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean = nativeThreadAnrSamplingAllowlistIgnored
    override fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod> = nativeThreadAnrSamplingAllowlistImpl
    override fun getNativeThreadAnrSamplingIntervalMs(): Long = 1000
}
