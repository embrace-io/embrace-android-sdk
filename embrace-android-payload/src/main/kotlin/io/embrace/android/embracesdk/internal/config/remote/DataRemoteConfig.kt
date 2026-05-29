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
)
