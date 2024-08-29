package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
public class CrashHandlerLocalConfig(
    @Json(name = "enabled")
    public val enabled: Boolean? = null
)
