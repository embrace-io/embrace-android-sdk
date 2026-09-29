package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the crash handler element specified in the Embrace config file.
 */
@Serializable
data class CrashHandlerLocalConfig(
    @SerialName("enabled")
    val enabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
