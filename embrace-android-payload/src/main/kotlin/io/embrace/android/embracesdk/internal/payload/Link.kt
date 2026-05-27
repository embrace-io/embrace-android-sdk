package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class Link(

    /* The ID of the span that this link is to */
    @SerialName("span_id")
    @Json(name = "span_id")
    val spanId: String? = null,

    @SerialName("trace_id")
    @Json(name = "trace_id")
    val traceId: String? = null,

    @SerialName("attributes")
    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,

    @SerialName("is_remote")
    @Json(name = "is_remote")
    val isRemote: Boolean? = null,
)
