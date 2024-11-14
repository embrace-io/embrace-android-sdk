package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.AllowedNdkSampleMethod
import io.embrace.android.embracesdk.internal.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.Unwinder

interface AnrBehavior : ConfigBehavior<EnabledFeatureConfig, AnrRemoteConfig> {

    /**
     * Percentage of users for which ANR stack trace capture is enabled.
     */
    fun isAnrCaptureEnabled(): Boolean

    /**
     * Time between stack trace captures for time intervals after the start of an ANR.
     */
    fun getSamplingIntervalMs(): Long

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
     * Minimum duration of an ANR interval
     */
    fun getMinDuration(): Int

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
