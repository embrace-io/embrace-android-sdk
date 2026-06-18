package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to thread blockage tracking
 */
@Serializable
data class ThreadBlockageRemoteConfig(

    @SerialName("pct_enabled")
    val pctEnabled: Int? = null,

    @SerialName("interval")
    val sampleIntervalMs: Long? = null,

    @SerialName("per_interval")
    val maxStacktracesPerInterval: Int? = null,

    @SerialName("max_depth")
    val stacktraceFrameLimit: Int? = null,

    @SerialName("per_session")
    val intervalsPerSession: Int? = null,

    @SerialName("min_duration")
    val minDuration: Int? = null,

    @SerialName("monitor_thread_priority")
    val monitorThreadPriority: Int? = null,
)
