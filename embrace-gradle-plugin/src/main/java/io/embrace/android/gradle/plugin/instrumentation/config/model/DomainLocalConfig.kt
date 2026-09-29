package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents each domain element specified in the Embrace config file.
 */
@Serializable
data class DomainLocalConfig(

    /**
     * Url for the domain.
     */
    @SerialName("domain_name")
    val domain: String,

    /**
     * Limit for the number of requests to be tracked.
     */
    @SerialName("domain_limit")
    val limit: Int,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
