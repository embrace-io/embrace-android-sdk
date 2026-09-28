package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the background activity configuration element specified in the Embrace config file.
 */
@Serializable
data class BackgroundActivityLocalConfig(
    @SerialName("capture_enabled")
    val backgroundActivityCaptureEnabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
