package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
data class BackgroundActivityLocalConfig(
    @Json(name = "capture_enabled")
    val backgroundActivityCaptureEnabled: Boolean? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
