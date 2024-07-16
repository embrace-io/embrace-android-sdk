package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.pm.ApplicationInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

internal class AppEnvironment(appInfo: ApplicationInfo) {
    val isDebug: Boolean = with(appInfo) { flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    @JsonClass(generateAdapter = false)
    internal enum class Environment(val value: String) {
        @Json(name = "dev")
        DEV("dev"),

        @Json(name = "prod")
        PROD("prod"),

        @Json(name = "UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
