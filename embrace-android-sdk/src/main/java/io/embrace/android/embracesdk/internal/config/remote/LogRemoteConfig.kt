package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Configuration values relating to the logs of the app.
 */
@JsonClass(generateAdapter = true)
internal data class LogRemoteConfig(

    /**
     * Used to truncate log messages.
     */
    @Json(name = "max_length")
    val logMessageMaximumAllowedLength: Int? = null,

    /**
     * Limit of info logs that user is able to send.
     */
    @Json(name = "info_limit")
    val logInfoLimit: Int? = null,

    /**
     * Limit of warning logs that user is able to send.
     */
    @Json(name = "warn_limit")
    val logWarnLimit: Int? = null,

    /**
     * Limit of error logs that user is able to send.
     */
    @Json(name = "error_limit")
    val logErrorLimit: Int? = null
)
