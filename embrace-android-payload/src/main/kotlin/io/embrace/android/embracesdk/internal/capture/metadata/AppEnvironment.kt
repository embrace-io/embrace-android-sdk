package io.embrace.android.embracesdk.internal.capture.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AppEnvironment(val isDebug: Boolean) {

    val environment: Environment = if (isDebug) Environment.DEV else Environment.PROD

    @Serializable
    enum class Environment(val value: String) {
        @SerialName("dev")
        DEV("dev"),

        @SerialName("prod")
        PROD("prod"),

        @SerialName("UNKNOWN")
        UNKNOWN("UNKNOWN")
    }
}
