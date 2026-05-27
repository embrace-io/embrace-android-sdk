package io.embrace.android.embracesdk.internal.capture.metadata

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AppEnvironment(val isDebug: Boolean) {

    val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    @Serializable
    @JsonClass(generateAdapter = false)
    enum class Environment(val value: String) {
        @SerialName("dev")
        @Json(name = "dev")
        DEV("dev"),

        @SerialName("prod")
        @Json(name = "prod")
        PROD("prod"),

        @SerialName("UNKNOWN")
        @Json(name = "UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
