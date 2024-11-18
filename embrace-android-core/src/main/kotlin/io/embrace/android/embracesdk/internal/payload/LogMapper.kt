package io.embrace.android.embracesdk.internal.payload

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.data.LogRecordData

@Suppress("DEPRECATION") // suppress for backwards compat
fun LogRecordData.toNewPayload(): Log {
    val isSpanContextValid = spanContext.isValid
    return Log(
        traceId = if (isSpanContextValid) spanContext.traceId else null,
        spanId = if (isSpanContextValid) spanContext.spanId else null,
        timeUnixNano = timestampEpochNanos,
        severityNumber = severity.severityNumber,
        severityText = severityText,
        body = body.asString(),
        attributes = attributes.toNewPayload()
    )
}

fun Attributes.toNewPayload(): List<Attribute> =
    this.asMap().entries.map { Attribute(it.key.key, it.value.toString()) }
