package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values relating to the ANR tracking on the app.
 */
@JsonClass(generateAdapter = true)
data class AnrRemoteConfig(

    @Json(name = "pct_enabled")
    val pctEnabled: Int? = null,

    @Json(name = "pct_pe_enabled")
    val pctAnrProcessErrorsEnabled: Int? = null,

    @Json(name = "pct_bg_enabled")
    val pctBgEnabled: Int? = null,

    @Json(name = "interval")
    val sampleIntervalMs: Long? = null,

    @Json(name = "anr_pe_interval")
    val anrProcessErrorsIntervalMs: Long? = null,

    @Json(name = "anr_pe_delay")
    val anrProcessErrorsDelayMs: Long? = null,

    @Json(name = "anr_pe_sc_extra_time")
    val anrProcessErrorsSchedulerExtraTimeAllowance: Long? = null,

    @Json(name = "per_interval")
    val maxStacktracesPerInterval: Int? = null,

    @Json(name = "max_depth")
    val stacktraceFrameLimit: Int? = null,

    @Json(name = "per_session")
    val anrPerSession: Int? = null,

    @Json(name = "main_thread_only")
    val mainThreadOnly: Boolean? = null,

    @Json(name = "priority")
    val minThreadPriority: Int? = null,

    @Json(name = "min_duration")
    val minDuration: Int? = null,

    @Json(name = "white_list")
    val allowList: List<String>? = null,

    @Json(name = "black_list")
    val blockList: List<String>? = null,

    @Json(name = "unity_ndk_sampling_factor")
    val nativeThreadAnrSamplingFactor: Int? = null,

    @Json(name = "unity_ndk_sampling_unwinder")
    val nativeThreadAnrSamplingUnwinder: String? = null,

    @Json(name = "pct_unity_thread_capture_enabled")
    val pctNativeThreadAnrSamplingEnabled: Float? = null,

    @Json(name = "ndk_sampling_offset_enabled")
    val nativeThreadAnrSamplingOffsetEnabled: Boolean? = null,

    @Json(name = "pct_idle_handler_enabled")
    val pctIdleHandlerEnabled: Float? = null,

    @Json(name = "pct_strictmode_listener_enabled")
    val pctStrictModeListenerEnabled: Float? = null,

    @Json(name = "strictmode_violation_limit")
    val strictModeViolationLimit: Int? = null,

    @Json(name = "ignore_unity_ndk_sampling_allowlist")
    val ignoreNativeThreadAnrSamplingAllowlist: Boolean? = null,

    @Json(name = "unity_ndk_sampling_allowlist")
    val nativeThreadAnrSamplingAllowlist: List<AllowedNdkSampleMethod>? = null,

    /**
     * Percentage of users for which Google ANR timestamp capture is enabled.
     */
    @Json(name = "google_pct_enabled")
    val googlePctEnabled: Int? = null,

    @Json(name = "monitor_thread_priority")
    val monitorThreadPriority: Int? = null
)
