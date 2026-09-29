package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
@Serializable
data class BaseUrlLocalConfig(
    @SerialName("config")
    val config: String? = null,

    @SerialName("data")
    val data: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
