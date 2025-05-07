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

    @Json(name = "interval")
    val sampleIntervalMs: Long? = null,

    @Json(name = "per_interval")
    val maxStacktracesPerInterval: Int? = null,

    @Json(name = "max_depth")
    val stacktraceFrameLimit: Int? = null,

    @Json(name = "per_session")
    val anrPerSession: Int? = null,

    @Json(name = "min_duration")
    val minDuration: Int? = null,

    @Json(name = "monitor_thread_priority")
    val monitorThreadPriority: Int? = null,
)
