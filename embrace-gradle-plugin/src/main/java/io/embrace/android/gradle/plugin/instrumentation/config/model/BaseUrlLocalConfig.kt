package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
@Serializable
data class BaseUrlLocalConfig(
    @Json(name = "config")
    @SerialName("config")
    val config: String? = null,

    @Json(name = "data")
    @SerialName("data")
    val data: String? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
