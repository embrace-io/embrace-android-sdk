package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values relating to data capture of the SDK
 */
@JsonClass(generateAdapter = true)
data class DataRemoteConfig(

    @Json(name = "pct_thermal_status_enabled")
    val pctThermalStatusEnabled: Float? = null
)
