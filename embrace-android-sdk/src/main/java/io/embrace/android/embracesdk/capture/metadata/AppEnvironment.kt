package io.embrace.android.embracesdk.capture.metadata

import android.content.pm.ApplicationInfo
import com.squareup.moshi.Json

internal class AppEnvironment(appInfo: ApplicationInfo) {
    val isDebug: Boolean = with(appInfo) { flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    internal enum class Environment(val value: String) {
        @Json(name = "dev")
        DEV("dev"),

        @Json(name = "prod")
        PROD("prod"),

        @Json(name = "UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
