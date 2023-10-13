package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Describes a particular Exception error. Where an exception error has a cause, there will be an
 * {@link ExceptionErrorInfo} for each nested cause.
 */
internal data class ExceptionErrorInfo(

    /**
     * Timestamp when exception error happened.
     */
    @SerializedName("ts") val timestamp: Long? = null,

    /**
     * App state (foreground or background).
     */
    @SerializedName("s") val state: String? = null,

    /**
     * A list of exceptions.
     */
    @SerializedName("ex") val exceptions: List<ExceptionInfo>? = null

)
