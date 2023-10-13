package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration values relating to the background activity capturing on the app.
 */
internal data class BackgroundActivityRemoteConfig(
    @SerializedName("threshold")
    val threshold: Float? = null
)
