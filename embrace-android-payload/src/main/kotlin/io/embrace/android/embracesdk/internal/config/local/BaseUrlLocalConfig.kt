package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
@JsonClass(generateAdapter = true)
class BaseUrlLocalConfig(
    @Json(name = "config") val config: String? = null,

    @Json(name = "data") val data: String? = null,

    @Json(name = "images") val images: String? = null
)
