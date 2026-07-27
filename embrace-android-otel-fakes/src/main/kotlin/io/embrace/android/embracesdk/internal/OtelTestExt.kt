
package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.opentelemetry.kotlin.aliases.OtelJavaStatusCode

fun Map<String, String>.toOtelJava(): OtelJavaAttributes {
    val builder = OtelJavaAttributes.builder()
    this.forEach { (key, value) ->
        builder.put(key, value)
    }
    return builder.build()
}

/**
 * Returns the attributes as a new Map<String, String>
 */
fun OtelJavaAttributes.toStringMap(): Map<String, String> = asMap().entries.associate {
    it.key.key.toString() to it.value.toString()
}

fun OtelJavaSpanData.toEmbracePayload(): Span = Span(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeNanos = startEpochNanos,
    endTimeNanos = endEpochNanos,
    status = when (status.statusCode) {
        OtelJavaStatusCode.UNSET -> Span.Status.UNSET
        OtelJavaStatusCode.OK -> Span.Status.OK
        OtelJavaStatusCode.ERROR -> Span.Status.ERROR
        else -> Span.Status.UNSET
    },
    events = events?.map { it.toEmbracePayload() } ?: emptyList(),
    attributes = attributes.toEmbracePayload(),
    links = links.map { it.toEmbracePayload() }
)

fun OtelJavaLinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote
)

private fun OtelJavaEventData.toEmbracePayload(): SpanEvent = SpanEvent(
    name = name,
    timestampNanos = epochNanos,
    attributes = attributes.toEmbracePayload(),
)

fun OtelJavaAttributes.toEmbracePayload(): List<Attribute> =
    this.asMap().entries.map { Attribute(it.key.key, it.value.toString()) }
