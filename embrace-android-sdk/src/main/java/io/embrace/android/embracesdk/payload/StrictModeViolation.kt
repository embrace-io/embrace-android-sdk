package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Represents information about a StrictMode violation that was captured on the device
 */
internal data class StrictModeViolation(

    /**
     * Information about the StrictMode violation
     */
    @SerializedName("n") val exceptionInfo: ExceptionInfo,

    /**
     * Timestamp in milliseconds at which the strictmode violation was captured
     */
    @SerializedName("ts") val timestamp: Long?
)
