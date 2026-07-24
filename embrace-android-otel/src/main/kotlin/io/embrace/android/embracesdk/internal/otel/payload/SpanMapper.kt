
package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.otel.spans.EmbraceLinkData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

fun EmbraceSpanEvent.toEmbracePayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = timestampNanos,
    attributes = attributes.toEmbracePayload(),
)

fun Map<String, String>.toEmbracePayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value) }

fun List<Attribute>.toEmbracePayload(): Map<String, String> =
    associate { Pair(it.key ?: "", it.data ?: "") }.filterKeys { it.isNotBlank() }

fun EmbraceLinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote,
)
