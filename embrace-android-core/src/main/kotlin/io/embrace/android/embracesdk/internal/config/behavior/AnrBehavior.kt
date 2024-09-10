package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import java.util.regex.Pattern

interface AnrBehavior {

    /**
     * Control whether SIGQUIT capture is enabled.
     */
    fun isSigquitCaptureEnabled(): Boolean

    /**
     * Allow listed threads by pattern
     */
    val allowPatternList: List<Pattern>

    /**
     * Black listed threads by pattern
     */
    val blockPatternList: List<Pattern>

    /**
     * Percentage of users for which ANR stack trace capture is enabled.
     */
    fun isAnrCaptureEnabled(): Boolean

    /**
     * Percentage of users for which ANR process errors capture is enabled.
     */
    fun isAnrProcessErrorsCaptureEnabled(): Boolean

    /**
     * The priority that should be used for the monitor thread.
     */
    fun getMonitorThreadPriority(): Int

    /**
     * Whether Background ANR capture is enabled.
     */
    fun isBgAnrCaptureEnabled(): Boolean

    /**
     * Time between stack trace captures for time intervals after the start of an ANR.
     */
    fun getSamplingIntervalMs(): Long

    /**
     * Time between ANR process errors checks for time intervals after the start of ANR process
     * errors check.
     */
    fun getAnrProcessErrorsIntervalMs(): Long

    /**
     * Time for which to delay the search of anr process errors. This would be the delay since
     * the thread has been blocked.
     */
    fun getAnrProcessErrorsDelayMs(): Long

    /**
     * This is the maximum time that the scheduler is allowed to keep on running since thread has
     * been unblocked.
     */
    fun getAnrProcessErrorsSchedulerExtraTimeAllowanceMs(): Long

    /**
     * Maximum captured stacktraces for a single ANR interval.
     */
    fun getMaxStacktracesPerInterval(): Int

    /**
     * Maximum number of frames to keep in ANR stacktrace samples
     */
    fun getStacktraceFrameLimit(): Int

    /**
     * Maximum captured anr for a session.
     */
    fun getMaxAnrIntervalsPerSession(): Int

    /**
     * The min thread priority that should be captured
     */
    fun getMinThreadPriority(): Int

    /**
     * Minimum duration of an ANR interval
     */
    fun getMinDuration(): Int

    /**
     * Whether only the main thread should be captured
     */
    fun shouldCaptureMainThreadOnly(): Boolean

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This should be multiplied by
     * the [getSamplingIntervalMs] to give the NDK sampling interval.
     */
    fun getNativeThreadAnrSamplingFactor(): Int

    /**
     * The unwinder used for native thread ANR stacktrace sampling.
     */
    fun getNativeThreadAnrSamplingUnwinder(): Unwinder

    /**
     * Whether Unity ANR capture is enabled
     */
    fun isUnityAnrCaptureEnabled(): Boolean

    /**
     * Whether offsets are enabled for native thread ANR stacktrace sampling.
     */
    fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean

    /**
     * The percentage of enabled devices for testing IdleHandler to terminate ANRs.
     */
    fun isIdleHandlerEnabled(): Boolean

    /**
     * Whether the StrictMode listener experiment is enabled.
     */
    fun isStrictModeListenerEnabled(): Boolean

    /**
     * Whether the Strictmode listener experiment is enabled.
     */
    fun getStrictModeViolationLimit(): Int

    /**
     * Whether the allow list is ignored or not.
     */
    fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean

    /**
     * The allowed list of classes/methods for NDK stacktrace sampling
     */
    fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod>

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This is calculated by
     * multiplying the [getSamplingIntervalMs] against [getNativeThreadAnrSamplingFactor].
     */
    fun getNativeThreadAnrSamplingIntervalMs(): Long
}
