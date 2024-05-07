package io.embrace.android.embracesdk.internal.spans

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class EmbraceSpanContext(
    @Json(name = "trace_id")
    val traceId: String,

    @Json(name = "span_id")
    val spanId: String,

    // TODO: Should we also add trace state and trace flags?
)
