package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
@Serializable
data class CrashHandlerLocalConfig(
    @Json(name = "enabled")
    @SerialName("enabled")
    val enabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
