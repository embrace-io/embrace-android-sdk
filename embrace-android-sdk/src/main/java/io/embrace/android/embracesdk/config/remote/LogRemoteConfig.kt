package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName

/**
 * Configuration values relating to the logs of the app.
 */
internal data class LogRemoteConfig(

    /**
     * Used to truncate log messages.
     */
    @SerializedName("max_length")
    val logMessageMaximumAllowedLength: Int? = null,

    /**
     * Limit of info logs that user is able to send.
     */
    @SerializedName("info_limit")
    val logInfoLimit: Int? = null,

    /**
     * Limit of warning logs that user is able to send.
     */
    @SerializedName("warn_limit")
    val logWarnLimit: Int? = null,

    /**
     * Limit of error logs that user is able to send.
     */
    @SerializedName("error_limit")
    val logErrorLimit: Int? = null
)
