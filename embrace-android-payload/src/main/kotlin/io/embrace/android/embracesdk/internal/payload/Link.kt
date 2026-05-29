package io.embrace.android.embracesdk.internal.payload

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Link(

    /* The ID of the span that this link is to */
    @SerialName("span_id")
    val spanId: String? = null,

    @SerialName("trace_id")
    val traceId: String? = null,

    @SerialName("attributes")
    val attributes: List<Attribute>? = null,

    @SerialName("is_remote")
    val isRemote: Boolean? = null,
)
