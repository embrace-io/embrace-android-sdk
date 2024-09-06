package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
class CrashHandlerLocalConfig(
    @Json(name = "enabled") val enabled: Boolean? = null
)
