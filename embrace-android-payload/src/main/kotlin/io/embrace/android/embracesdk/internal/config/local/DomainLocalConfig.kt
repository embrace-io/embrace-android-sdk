package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents each domain element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
public class DomainLocalConfig(

    /**
     * Url for the domain.
     */
    @Json(name = "domain_name")
    public val domain: String? = null,

    /**
     * Limit for the number of requests to be tracked.
     */
    @Json(name = "domain_limit")
    public val limit: Int? = null
)
