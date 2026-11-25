package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.kotlin.logging.data.LogRecordData

/**
 * This record as Embrace's payload has it, with [extraAttributes] after the record's own, except those the record already has: what the
 * payload carries that the record does not.
 */
fun LogRecordData.toEmbracePayload(extraAttributes: List<Attribute> = emptyList()): Log {
    val isSpanContextValid = spanContext.isValid
    val record = attributes
    return Log(
        traceId = if (isSpanContextValid) spanContext.traceId else null,
        spanId = if (isSpanContextValid) spanContext.spanId else null,
        timeUnixNano = timestamp,
        severityNumber = severityNumber?.ordinal,
        severityText = severityText,
        body = body?.toPayloadString(),
        attributes = if (extraAttributes.isEmpty()) {
            record.map { (key, value) -> Attribute(key, value.toPayloadString()) }
        } else {
            ArrayList<Attribute>(record.size + extraAttributes.size).also { payload ->
                record.forEach { (key, value) -> payload.add(Attribute(key, value.toPayloadString())) }
                // a key the record already has is not added a second time
                extraAttributes.forEach { extra -> if (!record.containsKey(extra.key)) payload.add(extra) }
            }
        },
    )
}
