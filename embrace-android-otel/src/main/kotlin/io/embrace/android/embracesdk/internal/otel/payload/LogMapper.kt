package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.kotlin.logging.data.LogRecordData

fun LogRecordData.toEmbracePayload(): Log = toEmbracePayload(attributes.toEmbraceAttributes())

/**
 * This log as Embrace's payload carries it, with [payloadAttributes] in place of the record's own.
 */
fun LogRecordData.toEmbracePayload(payloadAttributes: List<Attribute>): Log {
    val isSpanContextValid = spanContext.isValid
    return Log(
        traceId = if (isSpanContextValid) spanContext.traceId else null,
        spanId = if (isSpanContextValid) spanContext.spanId else null,
        timeUnixNano = timestamp,
        severityNumber = severityNumber?.ordinal,
        severityText = severityText,
        body = body?.toPayloadString(),
        attributes = payloadAttributes,
    )
}

fun Map<String, Any>.toEmbraceAttributes(): List<Attribute> = map { (key, value) -> Attribute(key, value.toPayloadString()) }
