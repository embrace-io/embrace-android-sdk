package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents each domain element specified in the Embrace config file.
 */
internal class DomainLocalConfig(

    /**
     * Url for the domain.
     */
    @SerializedName("domain_name")
    val domain: String? = null,

    /**
     * Limit for the number of requests to be tracked.
     */
    @SerializedName("domain_limit")
    val limit: Int? = null
)
