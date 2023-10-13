package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration values relating to the ANR tracking on the app.
 */
internal data class AnrRemoteConfig(

    @SerializedName("pct_enabled")
    val pctEnabled: Int? = null,

    @SerializedName("pct_pe_enabled")
    val pctAnrProcessErrorsEnabled: Int? = null,

    @SerializedName("pct_bg_enabled")
    val pctBgEnabled: Int? = null,

    @SerializedName("interval")
    val sampleIntervalMs: Long? = null,

    @SerializedName("anr_pe_interval")
    val anrProcessErrorsIntervalMs: Long? = null,

    @SerializedName("anr_pe_delay")
    val anrProcessErrorsDelayMs: Long? = null,

    @SerializedName("anr_pe_sc_extra_time")
    val anrProcessErrorsSchedulerExtraTimeAllowance: Long? = null,

    @SerializedName("per_interval")
    val maxStacktracesPerInterval: Int? = null,

    @SerializedName("max_depth")
    val stacktraceFrameLimit: Int? = null,

    @SerializedName("per_session")
    val anrPerSession: Int? = null,

    @SerializedName("main_thread_only")
    val mainThreadOnly: Boolean? = null,

    @SerializedName("priority")
    val minThreadPriority: Int? = null,

    @SerializedName("min_duration")
    val minDuration: Int? = null,

    @SerializedName("white_list")
    val allowList: List<String>? = null,

    @SerializedName("black_list")
    val blockList: List<String>? = null,

    @SerializedName("unity_ndk_sampling_factor")
    val nativeThreadAnrSamplingFactor: Int? = null,

    @SerializedName("unity_ndk_sampling_unwinder")
    val nativeThreadAnrSamplingUnwinder: String? = null,

    @SerializedName("pct_unity_thread_capture_enabled")
    val pctNativeThreadAnrSamplingEnabled: Float? = null,

    @SerializedName("ndk_sampling_offset_enabled")
    val nativeThreadAnrSamplingOffsetEnabled: Boolean? = null,

    @SerializedName("pct_idle_handler_enabled")
    val pctIdleHandlerEnabled: Float? = null,

    @SerializedName("pct_strictmode_listener_enabled")
    val pctStrictModeListenerEnabled: Float? = null,

    @SerializedName("strictmode_violation_limit")
    val strictModeViolationLimit: Int? = null,

    @SerializedName("ignore_unity_ndk_sampling_allowlist")
    val ignoreNativeThreadAnrSamplingAllowlist: Boolean? = null,

    @SerializedName("unity_ndk_sampling_allowlist")
    val nativeThreadAnrSamplingAllowlist: List<AllowedNdkSampleMethod>? = null,

    /**
     * Percentage of users for which Google ANR timestamp capture is enabled.
     */
    @SerializedName("google_pct_enabled")
    val googlePctEnabled: Int? = null,

    @SerializedName("monitor_thread_priority")
    val monitorThreadPriority: Int? = null
) {

    enum class Unwinder(internal val code: Int) {
        LIBUNWIND(0),
        LIBUNWINDSTACK(1);
    }

    internal class AllowedNdkSampleMethod(
        @SerializedName("c") val clz: String? = null,
        @SerializedName("m") val method: String? = null
    )
}
