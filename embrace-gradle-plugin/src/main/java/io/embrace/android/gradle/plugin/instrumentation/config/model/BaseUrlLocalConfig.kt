package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
data class BaseUrlLocalConfig(
    @Json(name = "config")
    val config: String? = null,

    @Json(name = "data")
    val data: String? = null
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
