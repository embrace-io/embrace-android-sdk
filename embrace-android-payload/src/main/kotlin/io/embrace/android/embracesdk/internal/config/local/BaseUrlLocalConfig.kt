package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
public class BaseUrlLocalConfig(
    @Json(name = "config")
    public val config: String? = null,

    @Json(name = "data")
    public val data: String? = null,

    @Json(name = "images")
    public val images: String? = null
)
