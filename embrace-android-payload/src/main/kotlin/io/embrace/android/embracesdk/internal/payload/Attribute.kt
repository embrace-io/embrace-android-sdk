package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A key-value pair that provides additional context to the span
 *
 * @param key The name of the attribute
 * @param data The value of the attribute
 */
@Serializable
@JsonClass(generateAdapter = true)
data class Attribute(

    /* The name of the attribute */
    @SerialName("key")
    @Json(name = "key")
    val key: String? = null,

    /* The value of the attribute */
    @SerialName("value")
    @Json(name = "value")
    val data: String? = null,
)
