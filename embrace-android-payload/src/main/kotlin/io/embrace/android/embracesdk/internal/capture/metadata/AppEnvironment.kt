package io.embrace.android.embracesdk.internal.capture.metadata

import android.content.pm.ApplicationInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

public class AppEnvironment(appInfo: ApplicationInfo) {
    public val isDebug: Boolean = with(appInfo) { flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    public val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    @JsonClass(generateAdapter = false)
    public enum class Environment(public val value: String) {
        @Json(name = "dev")
        DEV("dev"),

        @Json(name = "prod")
        PROD("prod"),

        @Json(name = "UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
