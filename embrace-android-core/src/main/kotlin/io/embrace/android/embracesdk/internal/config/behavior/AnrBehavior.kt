package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.Unwinder
import java.util.regex.Pattern

public interface AnrBehavior {

    /**
     * Control whether Google ANR capture is enabled.
     */
    public fun isGoogleAnrCaptureEnabled(): Boolean

    /**
     * Allow listed threads by pattern
     */
    public val allowPatternList: List<Pattern>

    /**
     * Black listed threads by pattern
     */
    public val blockPatternList: List<Pattern>

    /**
     * Percentage of users for which ANR stack trace capture is enabled.
     */
    public fun isAnrCaptureEnabled(): Boolean

    /**
     * Percentage of users for which ANR process errors capture is enabled.
     */
    public fun isAnrProcessErrorsCaptureEnabled(): Boolean

    /**
     * The priority that should be used for the monitor thread.
     */
    public fun getMonitorThreadPriority(): Int

    /**
     * Whether Background ANR capture is enabled.
     */
    public fun isBgAnrCaptureEnabled(): Boolean

    /**
     * Time between stack trace captures for time intervals after the start of an ANR.
     */
    public fun getSamplingIntervalMs(): Long

    /**
     * Time between ANR process errors checks for time intervals after the start of ANR process
     * errors check.
     */
    public fun getAnrProcessErrorsIntervalMs(): Long

    /**
     * Time for which to delay the search of anr process errors. This would be the delay since
     * the thread has been blocked.
     */
    public fun getAnrProcessErrorsDelayMs(): Long

    /**
     * This is the maximum time that the scheduler is allowed to keep on running since thread has
     * been unblocked.
     */
    public fun getAnrProcessErrorsSchedulerExtraTimeAllowanceMs(): Long

    /**
     * Maximum captured stacktraces for a single ANR interval.
     */
    public fun getMaxStacktracesPerInterval(): Int

    /**
     * Maximum number of frames to keep in ANR stacktrace samples
     */
    public fun getStacktraceFrameLimit(): Int

    /**
     * Maximum captured anr for a session.
     */
    public fun getMaxAnrIntervalsPerSession(): Int

    /**
     * The min thread priority that should be captured
     */
    public fun getMinThreadPriority(): Int

    /**
     * Minimum duration of an ANR interval
     */
    public fun getMinDuration(): Int

    /**
     * Whether only the main thread should be captured
     */
    public fun shouldCaptureMainThreadOnly(): Boolean

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This should be multiplied by
     * the [getSamplingIntervalMs] to give the NDK sampling interval.
     */
    public fun getNativeThreadAnrSamplingFactor(): Int

    /**
     * The unwinder used for native thread ANR stacktrace sampling.
     */
    public fun getNativeThreadAnrSamplingUnwinder(): Unwinder

    /**
     * Whether native thread ANR sampling is enabled
     */
    public fun isNativeThreadAnrSamplingEnabled(): Boolean

    /**
     * Whether offsets are enabled for native thread ANR stacktrace sampling.
     */
    public fun isNativeThreadAnrSamplingOffsetEnabled(): Boolean

    /**
     * The percentage of enabled devices for testing IdleHandler to terminate ANRs.
     */
    public fun isIdleHandlerEnabled(): Boolean

    /**
     * Whether the StrictMode listener experiment is enabled.
     */
    public fun isStrictModeListenerEnabled(): Boolean

    /**
     * Whether the Strictmode listener experiment is enabled.
     */
    public fun getStrictModeViolationLimit(): Int

    /**
     * Whether the allow list is ignored or not.
     */
    public fun isNativeThreadAnrSamplingAllowlistIgnored(): Boolean

    /**
     * The allowed list of classes/methods for NDK stacktrace sampling
     */
    public fun getNativeThreadAnrSamplingAllowlist(): List<AllowedNdkSampleMethod>

    /**
     * The sampling factor for native thread ANR stacktrace sampling. This is calculated by
     * multiplying the [getSamplingIntervalMs] against [getNativeThreadAnrSamplingFactor].
     */
    public fun getNativeThreadAnrSamplingIntervalMs(): Long
}
