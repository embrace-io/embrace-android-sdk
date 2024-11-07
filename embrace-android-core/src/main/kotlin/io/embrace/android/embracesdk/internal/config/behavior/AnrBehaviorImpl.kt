package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Provides the behavior that the ANR feature should follow.
 */
class AnrBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<AnrRemoteConfig?>,
    private val instrumentedConfig: InstrumentedConfig,
) : AnrBehavior, MergedConfigBehavior<UnimplementedConfig, AnrRemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

    private companion object {
        private const val DEFAULT_ANR_PCT_ENABLED = true
        private const val DEFAULT_ANR_INTERVAL_MS: Long = 100
        private const val DEFAULT_ANR_MAX_PER_INTERVAL = 80
        private const val DEFAULT_STACKTRACE_FRAME_LIMIT = 200
        private const val DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION = 5
        private const val DEFAULT_ANR_MIN_CAPTURE_DURATION = 1000
        private const val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_FACTOR = 5
        private const val DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED = true
        private const val DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = true
        private val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = listOf(
            AllowedNdkSampleMethod("UnityPlayer", "pauseUnity")
        )
    }

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

    override fun getNativeThreadAnrSamplingFactor(): Int =
        remote?.nativeThreadAnrSamplingFactor ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_FACTOR

    override fun getNativeThreadAnrSamplingUnwinder(): Unwinder {
        return runCatching {
            Unwinder.values().find {
                it.name.equals(remote?.nativeThreadAnrSamplingUnwinder, true)
            } ?: Unwinder.LIBUNWIND
        }.getOrDefault(Unwinder.LIBUNWIND)
    }

    override fun isUnityAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctNativeThreadAnrSamplingEnabled)
            ?: instrumentedConfig.enabledFeatures.isUnityAnrCaptureEnabled()
    }

    override fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean =
        remote?.nativeThreadAnrSamplingOffsetEnabled ?: DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED

    override fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean =
        remote?.ignoreNativeThreadAnrSamplingAllowlist
            ?: DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    override fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod> =
        remote?.nativeThreadAnrSamplingAllowlist ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    override fun getNativeThreadAnrSamplingIntervalMs(): Long =
        getSamplingIntervalMs() * getNativeThreadAnrSamplingFactor()
}
