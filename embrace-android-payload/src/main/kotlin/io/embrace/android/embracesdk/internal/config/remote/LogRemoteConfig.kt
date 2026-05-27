package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the logs of the app.
 */
@Serializable
@JsonClass(generateAdapter = true)
data class LogRemoteConfig(

    /**
     * Used to truncate log messages.
     */
    @SerialName("max_length")
    @Json(name = "max_length")
    val logMessageMaximumAllowedLength: Int? = null,

    /**
     * Limit of info logs that user is able to send.
     */
    @SerialName("info_limit")
    @Json(name = "info_limit")
    val logInfoLimit: Int? = null,

    /**
     * Limit of warning logs that user is able to send.
     */
    @SerialName("warn_limit")
    @Json(name = "warn_limit")
    val logWarnLimit: Int? = null,

    /**
     * Limit of error logs that user is able to send.
     */
    @SerialName("error_limit")
    @Json(name = "error_limit")
    val logErrorLimit: Int? = null,
)
