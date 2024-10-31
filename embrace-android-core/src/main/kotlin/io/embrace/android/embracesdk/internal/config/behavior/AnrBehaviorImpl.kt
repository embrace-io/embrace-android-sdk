package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.regex.Pattern

/**
 * Provides the behavior that the ANR feature should follow.
 */
class AnrBehaviorImpl(
    thresholdCheck: BehaviorThresholdCheck,
    remoteSupplier: Provider<AnrRemoteConfig?>,
) : AnrBehavior, MergedConfigBehavior<UnimplementedConfig, AnrRemoteConfig>(
    thresholdCheck = thresholdCheck,
    remoteSupplier = remoteSupplier
) {

    private companion object {
        private const val DEFAULT_ANR_PCT_ENABLED = true
        private const val DEFAULT_ANR_PROCESS_ERRORS_PCT_ENABLED = false
        private const val DEFAULT_ANR_BG_PCT_ENABLED = false
        private const val DEFAULT_ANR_INTERVAL_MS: Long = 100
        private const val DEFAULT_ANR_PROCESS_ERRORS_INTERVAL_MS: Long = 1000
        private const val DEFAULT_ANR_PROCESS_ERRORS_DELAY_MS: Long = 5 * 1000
        private const val DEFAULT_ANR_PROCESS_ERRORS_SCHEDULER_EXTRA_TIME_ALLOWANCE: Long =
            30 * 1000
        private const val DEFAULT_ANR_MAX_PER_INTERVAL = 80
        private const val DEFAULT_STACKTRACE_FRAME_LIMIT = 200
        private const val DEFAULT_ANR_MIN_THREAD_PRIORITY_TO_CAPTURE = 0
        private const val DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION = 5
        private const val DEFAULT_ANR_MIN_CAPTURE_DURATION = 1000
        private const val DEFAULT_ANR_MAIN_THREAD_ONLY = true
        private const val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_FACTOR = 5
        private const val DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED = true
        private const val DEFAULT_IDLE_HANDLER_ENABLED = false
        private const val DEFAULT_STRICT_MODE_LISTENER_ENABLED = false
        private const val DEFAULT_STRICT_MODE_VIOLATION_LIMIT = 25
        private const val DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = true
        private val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = listOf(
            AllowedNdkSampleMethod("UnityPlayer", "pauseUnity")
        )
        private const val DEFAULT_MONITOR_THREAD_PRIORITY =
            android.os.Process.THREAD_PRIORITY_DEFAULT
    }

    override val allowPatternList: List<Pattern> by lazy {
        remote?.allowList?.map(Pattern::compile) ?: emptyList()
    }

    override val blockPatternList: List<Pattern> by lazy {
        remote?.blockList?.map(Pattern::compile) ?: emptyList()
    }

    override fun isAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled)
            ?: DEFAULT_ANR_PCT_ENABLED
    }

    override fun isAnrProcessErrorsCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctAnrProcessErrorsEnabled)
            ?: DEFAULT_ANR_PROCESS_ERRORS_PCT_ENABLED
    }

    override fun getMonitorThreadPriority(): Int =
        remote?.monitorThreadPriority ?: DEFAULT_MONITOR_THREAD_PRIORITY

    override fun isBgAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctBgEnabled)
            ?: DEFAULT_ANR_BG_PCT_ENABLED
    }

    override fun getSamplingIntervalMs(): Long = remote?.sampleIntervalMs ?: DEFAULT_ANR_INTERVAL_MS

    override fun getAnrProcessErrorsIntervalMs(): Long =
        remote?.anrProcessErrorsIntervalMs ?: DEFAULT_ANR_PROCESS_ERRORS_INTERVAL_MS

    override fun getAnrProcessErrorsDelayMs(): Long =
        remote?.anrProcessErrorsDelayMs ?: DEFAULT_ANR_PROCESS_ERRORS_DELAY_MS

    override fun getAnrProcessErrorsSchedulerExtraTimeAllowanceMs(): Long =
        remote?.anrProcessErrorsSchedulerExtraTimeAllowance
            ?: DEFAULT_ANR_PROCESS_ERRORS_SCHEDULER_EXTRA_TIME_ALLOWANCE

    override fun getMaxStacktracesPerInterval(): Int =
        remote?.maxStacktracesPerInterval ?: DEFAULT_ANR_MAX_PER_INTERVAL

    override fun getStacktraceFrameLimit(): Int =
        remote?.stacktraceFrameLimit ?: DEFAULT_STACKTRACE_FRAME_LIMIT

    override fun getMaxAnrIntervalsPerSession(): Int =
        remote?.anrPerSession ?: DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION

    override fun getMinThreadPriority(): Int =
        remote?.minThreadPriority ?: DEFAULT_ANR_MIN_THREAD_PRIORITY_TO_CAPTURE

    override fun getMinDuration(): Int = remote?.minDuration ?: DEFAULT_ANR_MIN_CAPTURE_DURATION

    override fun shouldCaptureMainThreadOnly(): Boolean {
        val r = remote
        val mainThreadOnly = r?.mainThreadOnly
        return mainThreadOnly ?: DEFAULT_ANR_MAIN_THREAD_ONLY
    }

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
            ?: InstrumentedConfig.enabledFeatures.isUnityAnrCaptureEnabled()
    }

    override fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean =
        remote?.nativeThreadAnrSamplingOffsetEnabled ?: DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED

    override fun isIdleHandlerEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctIdleHandlerEnabled)
            ?: DEFAULT_IDLE_HANDLER_ENABLED
    }

    override fun isStrictModeListenerEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctStrictModeListenerEnabled)
            ?: DEFAULT_STRICT_MODE_LISTENER_ENABLED
    }

    override fun getStrictModeViolationLimit(): Int =
        remote?.strictModeViolationLimit ?: DEFAULT_STRICT_MODE_VIOLATION_LIMIT

    override fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean =
        remote?.ignoreNativeThreadAnrSamplingAllowlist
            ?: DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    override fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod> =
        remote?.nativeThreadAnrSamplingAllowlist ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    override fun getNativeThreadAnrSamplingIntervalMs(): Long =
        getSamplingIntervalMs() * getNativeThreadAnrSamplingFactor()
}
