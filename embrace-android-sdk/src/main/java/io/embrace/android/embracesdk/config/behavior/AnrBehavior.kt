package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.AnrLocalConfig
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig.Unwinder
import java.util.regex.Pattern

/**
 * Provides the behavior that the ANR feature should follow.
 */
internal class AnrBehavior(
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: () -> AnrLocalConfig?,
    remoteSupplier: () -> AnrRemoteConfig?
) : MergedConfigBehavior<AnrLocalConfig, AnrRemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {
        private const val CAPTURE_GOOGLE_DEFAULT = false
        private const val DEFAULT_ANR_PCT_ENABLED = true
        private const val DEFAULT_ANR_PROCESS_ERRORS_PCT_ENABLED = false
        private const val DEFAULT_ANR_BG_PCT_ENABLED = false
        private const val DEFAULT_ANR_INTERVAL_MS: Long = 100
        private const val DEFAULT_ANR_PROCESS_ERRORS_INTERVAL_MS: Long = 1000
        private const val DEFAULT_ANR_PROCESS_ERRORS_DELAY_MS: Long = 5 * 1000
        private const val DEFAULT_ANR_PROCESS_ERRORS_SCHEDULER_EXTRA_TIME_ALLOWANCE: Long =
            30 * 1000
        private const val DEFAULT_ANR_MAX_PER_INTERVAL = 80
        private const val DEFAULT_STACKTRACE_FRAME_LIMIT = 100
        private const val DEFAULT_ANR_MIN_THREAD_PRIORITY_TO_CAPTURE = 0
        private const val DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION = 5
        private const val DEFAULT_ANR_MIN_CAPTURE_DURATION = 1000
        private const val DEFAULT_ANR_MAIN_THREAD_ONLY = true
        private const val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_FACTOR = 5
        private const val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ENABLED = false
        private const val DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED = true
        private const val DEFAULT_IDLE_HANDLER_ENABLED = false
        private const val DEFAULT_STRICT_MODE_LISTENER_ENABLED = false
        private const val DEFAULT_STRICT_MODE_VIOLATION_LIMIT = 25
        private const val DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = true
        private val DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST = listOf(
            AnrRemoteConfig.AllowedNdkSampleMethod("UnityPlayer", "pauseUnity")
        )
        private const val DEFAULT_MONITOR_THREAD_PRIORITY =
            android.os.Process.THREAD_PRIORITY_DEFAULT
    }

    /**
     * Control whether Google ANR capture is enabled.
     */
    fun isGoogleAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.googlePctEnabled)
            ?: local?.captureGoogle
            ?: CAPTURE_GOOGLE_DEFAULT
    }

    /**
     * Allow listed threads by pattern
     */
    val allowPatternList: List<Pattern> by lazy {
        remote?.allowList?.map(Pattern::compile) ?: emptyList()
    }

    /**
     * Black listed threads by pattern
     */
    val blockPatternList: List<Pattern> by lazy {
        remote?.blockList?.map(Pattern::compile) ?: emptyList()
    }

    /**
     * Percentage of users for which ANR stack trace capture is enabled.
     */
    fun isAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctEnabled)
            ?: DEFAULT_ANR_PCT_ENABLED
    }

    /**
     * Percentage of users for which ANR process errors capture is enabled.
     */
    fun isAnrProcessErrorsCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctAnrProcessErrorsEnabled)
            ?: DEFAULT_ANR_PROCESS_ERRORS_PCT_ENABLED
    }

    /**
     * The priority that should be used for the monitor thread.
     */
    fun getMonitorThreadPriority(): Int =
        remote?.monitorThreadPriority ?: DEFAULT_MONITOR_THREAD_PRIORITY

    /**
     * Whether Background ANR capture is enabled.
     */
    fun isBgAnrCaptureEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctBgEnabled)
            ?: DEFAULT_ANR_BG_PCT_ENABLED
    }

    /**
     * Time between stack trace captures for time intervals after the start of an ANR.
     */
    fun getSamplingIntervalMs(): Long = remote?.sampleIntervalMs ?: DEFAULT_ANR_INTERVAL_MS

    /**
     * Time between ANR process errors checks for time intervals after the start of ANR process
     * errors check.
     */
    fun getAnrProcessErrorsIntervalMs(): Long =
        remote?.anrProcessErrorsIntervalMs ?: DEFAULT_ANR_PROCESS_ERRORS_INTERVAL_MS

    /**
     * Time for which to delay the search of anr process errors. This would be the delay since
     * the thread has been blocked.
     */
    fun getAnrProcessErrorsDelayMs(): Long =
        remote?.anrProcessErrorsDelayMs ?: DEFAULT_ANR_PROCESS_ERRORS_DELAY_MS

    /**
     * This is the maximum time that the scheduler is allowed to keep on running since thread has
     * been unblocked.
     */
    fun getAnrProcessErrorsSchedulerExtraTimeAllowanceMs(): Long =
        remote?.anrProcessErrorsSchedulerExtraTimeAllowance
            ?: DEFAULT_ANR_PROCESS_ERRORS_SCHEDULER_EXTRA_TIME_ALLOWANCE

    /**
     * Maximum captured stacktraces for a single ANR interval.
     */
    fun getMaxStacktracesPerInterval(): Int =
        remote?.maxStacktracesPerInterval ?: DEFAULT_ANR_MAX_PER_INTERVAL

    /**
     * Maximum number of frames to keep in ANR stacktrace samples
     */
    fun getStacktraceFrameLimit(): Int =
        remote?.stacktraceFrameLimit ?: DEFAULT_STACKTRACE_FRAME_LIMIT

    /**
     * Maximum captured anr for a session.
     */
    fun getMaxAnrIntervalsPerSession(): Int =
        remote?.anrPerSession ?: DEFAULT_ANR_MAX_ANR_INTERVALS_PER_SESSION

    /**
     * The min thread priority that should be captured
     */
    fun getMinThreadPriority(): Int =
        remote?.minThreadPriority ?: DEFAULT_ANR_MIN_THREAD_PRIORITY_TO_CAPTURE

    /**
     * Minimum duration of an ANR interval
     */
    fun getMinDuration(): Int = remote?.minDuration ?: DEFAULT_ANR_MIN_CAPTURE_DURATION

    /**
     * Whether only the main thread should be captured
     */
    fun shouldCaptureMainThreadOnly(): Boolean =
        remote?.mainThreadOnly ?: DEFAULT_ANR_MAIN_THREAD_ONLY

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This should be multiplied by
     * the [getSamplingIntervalMs] to give the NDK sampling interval.
     */
    fun getNativeThreadAnrSamplingFactor(): Int =
        remote?.nativeThreadAnrSamplingFactor ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_FACTOR

    /**
     * The unwinder used for native thread ANR stacktrace sampling.
     */
    fun getNativeThreadAnrSamplingUnwinder(): Unwinder {
        return runCatching {
            Unwinder.values().find {
                it.name.equals(remote?.nativeThreadAnrSamplingUnwinder, true)
            } ?: Unwinder.LIBUNWIND
        }.getOrDefault(Unwinder.LIBUNWIND)
    }

    /**
     * Whether native thread ANR sampling is enabled
     */
    fun isNativeThreadAnrSamplingEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctNativeThreadAnrSamplingEnabled)
            ?: local?.captureUnityThread
            ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ENABLED
    }

    /**
     * Whether offsets are enabled for native thread ANR stacktrace sampling.
     */
    fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean =
        remote?.nativeThreadAnrSamplingOffsetEnabled ?: DEFAULT_NATIVE_THREAD_ANR_OFFSET_ENABLED

    /**
     * The percentage of enabled devices for testing IdleHandler to terminate ANRs.
     */
    fun isIdleHandlerEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctIdleHandlerEnabled)
            ?: DEFAULT_IDLE_HANDLER_ENABLED
    }

    /**
     * Whether the StrictMode listener experiment is enabled.
     */
    fun isStrictModeListenerEnabled(): Boolean {
        return thresholdCheck.isBehaviorEnabled(remote?.pctStrictModeListenerEnabled)
            ?: DEFAULT_STRICT_MODE_LISTENER_ENABLED
    }

    /**
     * Whether the Strictmode listener experiment is enabled.
     */
    fun getStrictModeViolationLimit(): Int =
        remote?.strictModeViolationLimit ?: DEFAULT_STRICT_MODE_VIOLATION_LIMIT

    /**
     * Whether the allow list is ignored or not.
     */
    fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean =
        remote?.ignoreNativeThreadAnrSamplingAllowlist
            ?: DEFAULT_IGNORE_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    /**
     * The allowed list of classes/methods for NDK stacktrace sampling
     */
    fun getNativeThreadAnrSamplingAllowlist(): List<AnrRemoteConfig.AllowedNdkSampleMethod> =
        remote?.nativeThreadAnrSamplingAllowlist ?: DEFAULT_NATIVE_THREAD_ANR_SAMPLING_ALLOWLIST

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This is calculated by
     * multiplying the [getSamplingIntervalMs] against [getNativeThreadAnrSamplingFactor].
     */
    fun getNativeThreadAnrSamplingIntervalMs() =
        getSamplingIntervalMs() * getNativeThreadAnrSamplingFactor()
}
