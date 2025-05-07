package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Link(

    /* The ID of the span that this link is to */
    @Json(name = "span_id")
    val spanId: String? = null,

    @Json(name = "trace_id")
    val traceId: String? = null,

    @Json(name = "attributes")
    val attributes: List<Attribute>? = null,

    @Json(name = "is_remote")
    val isRemote: Boolean? = null,
)
