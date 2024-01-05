package io.embrace.android.embracesdk.payload.otel

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData

@JsonClass(generateAdapter = true)
internal data class Envelope(

    val type: String,

    val data: EnvelopeData
)

internal interface EnvelopeData

internal data class SessionData (
    val spans: List<EmbraceSpanData>? = null,
) : EnvelopeData