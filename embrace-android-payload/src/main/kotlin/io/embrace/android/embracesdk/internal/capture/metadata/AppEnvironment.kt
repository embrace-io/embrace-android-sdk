package io.embrace.android.embracesdk.internal.capture.metadata

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

class AppEnvironment(val isDebug: Boolean) {

    val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    @JsonClass(generateAdapter = false)
    enum class Environment(val value: String) {
        @Json(name = "dev")
        DEV("dev"),

        @Json(name = "prod")
        PROD("prod"),

        @Json(name = "UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
