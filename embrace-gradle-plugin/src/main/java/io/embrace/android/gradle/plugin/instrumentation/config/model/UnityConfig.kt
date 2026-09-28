package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unity-specific configuration.
 */
@Serializable
data class UnityConfig(
    @SerialName("symbols_archive_name")
    val symbolsArchiveName: String?,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
