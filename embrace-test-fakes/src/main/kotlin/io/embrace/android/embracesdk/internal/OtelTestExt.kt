@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaEventData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLinkData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.opentelemetry.api.trace.StatusCode

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

@OptIn(ExperimentalApi::class)
fun OtelJavaSpanData.toEmbraceSpanData(): EmbraceSpanData = EmbraceSpanData(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    parentSpanId = parentSpanId,
    name = name,
    startTimeNanos = startEpochNanos,
    endTimeNanos = endEpochNanos,
    status = when (status.statusCode) {
        StatusCode.UNSET -> io.embrace.opentelemetry.kotlin.tracing.StatusCode.UNSET
        StatusCode.OK -> io.embrace.opentelemetry.kotlin.tracing.StatusCode.OK
        StatusCode.ERROR -> io.embrace.opentelemetry.kotlin.tracing.StatusCode.ERROR
        else -> io.embrace.opentelemetry.kotlin.tracing.StatusCode.UNSET
    },
    events = events?.mapNotNull { it.toEmbracePayload() } ?: emptyList(),
    attributes = attributes.toStringMap(),
    links = links.map { it.toEmbracePayload() }
)

fun OtelJavaLinkData.toEmbracePayload() = Link(
    spanId = spanContext.spanId,
    traceId = spanContext.traceId,
    attributes = attributes.toEmbracePayload(),
    isRemote = spanContext.isRemote
)

private fun OtelJavaEventData.toEmbracePayload(): EmbraceSpanEvent? {
    return EmbraceSpanEvent.create(
        name = name,
        timestampMs = epochNanos.nanosToMillis(),
        attributes = attributes.toStringMap(),
    )
}

fun OtelJavaAttributes.toEmbracePayload(): List<Attribute> =
    this.asMap().entries.map { Attribute(it.key.key, it.value.toString()) }
