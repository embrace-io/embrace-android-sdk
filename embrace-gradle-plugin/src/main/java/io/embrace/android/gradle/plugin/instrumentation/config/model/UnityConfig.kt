package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unity-specific configuration.
 */
@JsonClass(generateAdapter = true)
@Serializable
data class UnityConfig(
    @Json(name = "symbols_archive_name")
    @SerialName("symbols_archive_name")
    val symbolsArchiveName: String?,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
