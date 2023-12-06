package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values for the spans feature
 */
@JsonClass(generateAdapter = true)
internal data class SpansRemoteConfig(
    @Json(name = "pct_enabled")
    val pctEnabled: Float? = null
)
