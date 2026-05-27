package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to thread blockage tracking
 */
@Serializable
@JsonClass(generateAdapter = true)
data class ThreadBlockageRemoteConfig(

    @SerialName("pct_enabled")
    @Json(name = "pct_enabled")
    val pctEnabled: Int? = null,

    @SerialName("interval")
    @Json(name = "interval")
    val sampleIntervalMs: Long? = null,

    @SerialName("per_interval")
    @Json(name = "per_interval")
    val maxStacktracesPerInterval: Int? = null,

    @SerialName("max_depth")
    @Json(name = "max_depth")
    val stacktraceFrameLimit: Int? = null,

    @SerialName("per_session")
    @Json(name = "per_session")
    val intervalsPerSession: Int? = null,

    @SerialName("min_duration")
    @Json(name = "min_duration")
    val minDuration: Int? = null,

    @SerialName("monitor_thread_priority")
    @Json(name = "monitor_thread_priority")
    val monitorThreadPriority: Int? = null,
)
