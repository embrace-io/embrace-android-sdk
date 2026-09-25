package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents each domain element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
@Serializable
data class DomainLocalConfig(

    /**
     * Url for the domain.
     */
    @Json(name = "domain_name")
    @SerialName("domain_name")
    val domain: String,

    /**
     * Limit for the number of requests to be tracked.
     */
    @Json(name = "domain_limit")
    @SerialName("domain_limit")
    val limit: Int,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
