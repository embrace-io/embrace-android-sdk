package io.embrace.android.embracesdk.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values relating to the background activity capturing on the app.
 */
@JsonClass(generateAdapter = true)
internal data class BackgroundActivityRemoteConfig(
    @Json(name = "threshold")
    val threshold: Float? = null
)
