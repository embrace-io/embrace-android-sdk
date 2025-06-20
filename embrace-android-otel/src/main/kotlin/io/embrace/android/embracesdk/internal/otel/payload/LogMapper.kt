package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData

@Suppress("DEPRECATION") // suppress for backwards compat
fun OtelJavaLogRecordData.toEmbracePayload(): Log {
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

fun OtelJavaAttributes.toEmbracePayload(): List<Attribute> =
    this.asMap().entries.map { Attribute(it.key.key, it.value.toString()) }
