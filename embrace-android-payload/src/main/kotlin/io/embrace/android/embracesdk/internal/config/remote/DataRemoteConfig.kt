package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to data capture of the SDK
 */
@Serializable
data class DataRemoteConfig(

    @SerialName("pct_thermal_status_enabled")
    val pctThermalStatusEnabled: Float? = null,

    /**
     * The fallback duration in milliseconds after which an in-flight network request span is assumed
     * to have leaked and is dropped. Used when the HTTP client exposes no call-level timeout of its own.
     */
    @SerialName("network_request_span_timeout_ms")
    val networkRequestSpanTimeoutMs: Long? = null,
)
