package io.embrace.android.embracesdk.internal.spans

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class EmbraceSpanLink(

    @Json(name = "span_context")
    val spanContext: EmbraceSpanContext,

    @Json(name = "attributes")
    val attributes: Map<String, String>
)
