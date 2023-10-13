package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
internal class CrashHandlerLocalConfig(
    @SerializedName("enabled")
    val enabled: Boolean? = null
)
