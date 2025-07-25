package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
fun ReadableLogRecord.toEmbracePayload(): Log {
    val isSpanContextValid = spanContext.isValid
    return Log(
        traceId = if (isSpanContextValid) spanContext.traceId else null,
        spanId = if (isSpanContextValid) spanContext.spanId else null,
        timeUnixNano = timestamp,
        severityNumber = severityNumber?.ordinal,
        severityText = severityText,
        body = body,
        attributes = attributes.map { (key, value) -> Attribute(key, value.toString()) }
    )
}
