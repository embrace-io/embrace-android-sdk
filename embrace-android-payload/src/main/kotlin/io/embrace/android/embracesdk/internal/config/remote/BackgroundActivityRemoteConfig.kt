package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the background activity capturing on the app.
 */
@Serializable
data class BackgroundActivityRemoteConfig(
    @SerialName("threshold")
    val threshold: Float? = null,
)
