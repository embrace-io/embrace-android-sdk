package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents information about a StrictMode violation that was captured on the device
 */
@JsonClass(generateAdapter = true)
internal data class StrictModeViolation(

    /**
     * Information about the StrictMode violation
     */
    @Json(name = "n") val exceptionInfo: ExceptionInfo,

    /**
     * Timestamp in milliseconds at which the strictmode violation was captured
     */
    @Json(name = "ts") val timestamp: Long?
)
