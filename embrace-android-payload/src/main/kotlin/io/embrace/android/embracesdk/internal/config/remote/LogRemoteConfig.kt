package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the logs of the app.
 */
@Serializable
data class LogRemoteConfig(

    /**
     * Used to truncate log messages.
     */
    @SerialName("max_length")
    val logMessageMaximumAllowedLength: Int? = null,

    /**
     * Limit of info logs that user is able to send.
     */
    @SerialName("info_limit")
    val logInfoLimit: Int? = null,

    /**
     * Limit of warning logs that user is able to send.
     */
    @SerialName("warn_limit")
    val logWarnLimit: Int? = null,

    /**
     * Limit of error logs that user is able to send.
     */
    @SerialName("error_limit")
    val logErrorLimit: Int? = null,
)
