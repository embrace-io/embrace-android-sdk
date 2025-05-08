package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.data.LogRecordData

@Suppress("DEPRECATION") // suppress for backwards compat
fun LogRecordData.toEmbracePayload(): Log {
    val isSpanContextValid = spanContext.isValid
    return Log(
        traceId = if (isSpanContextValid) spanContext.traceId else null,
        spanId = if (isSpanContextValid) spanContext.spanId else null,
        timeUnixNano = timestampEpochNanos,
        severityNumber = severity.severityNumber,
        severityText = severityText,
        body = body.asString(),
        attributes = attributes.toEmbracePayload()
    )
}

const val MAX_PROPERTY_SIZE = 10

fun Attributes.toEmbracePayload(): List<Attribute> =
    this.asMap().entries
        .map { Attribute(it.key.key, it.value.toString()) }
        .take(MAX_PROPERTY_SIZE)
