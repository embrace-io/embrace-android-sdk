package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to data capture of the SDK
 */
@Serializable
@JsonClass(generateAdapter = true)
data class DataRemoteConfig(

    @SerialName("pct_thermal_status_enabled")
    @Json(name = "pct_thermal_status_enabled")
    val pctThermalStatusEnabled: Float? = null,
)
