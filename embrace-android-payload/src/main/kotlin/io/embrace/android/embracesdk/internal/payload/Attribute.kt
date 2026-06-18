package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A key-value pair that provides additional context to the span
 *
 * @param key The name of the attribute
 * @param data The value of the attribute
 */
@Serializable
data class Attribute(

    /* The name of the attribute */
    @SerialName("key")
    val key: String? = null,

    /* The value of the attribute */
    @SerialName("value")
    val data: String? = null,
)
