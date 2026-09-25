package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
@Serializable
data class BackgroundActivityLocalConfig(
    @Json(name = "capture_enabled")
    @SerialName("capture_enabled")
    val backgroundActivityCaptureEnabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
