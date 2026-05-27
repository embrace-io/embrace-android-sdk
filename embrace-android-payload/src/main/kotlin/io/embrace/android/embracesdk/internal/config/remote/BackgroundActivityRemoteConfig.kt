package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the background activity capturing on the app.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class BackgroundActivityRemoteConfig(
    @SerialName("threshold")
    @Json(name = "threshold")
    val threshold: Float? = null,
)
