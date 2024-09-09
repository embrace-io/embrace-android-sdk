package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A key-value pair that provides additional context to the span
 *
 * @param key The name of the attribute
 * @param data The value of the attribute
 */
@JsonClass(generateAdapter = true)
data class Attribute(

    /* The name of the attribute */
    @Json(name = "key")
    val key: String? = null,

    /* The value of the attribute */
    @Json(name = "value")
    val data: String? = null
)
