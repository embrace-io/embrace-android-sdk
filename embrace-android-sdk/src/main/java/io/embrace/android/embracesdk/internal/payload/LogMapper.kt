package io.embrace.android.embracesdk.internal.payload

import io.opentelemetry.sdk.logs.data.LogRecordData

internal fun LogRecordData.toNewPayload(): Log = Log(
    timeUnixNano = observedTimestampEpochNanos,
    severityNumber = severity.severityNumber,
    severityText = severityText,
    body = LogBody(body.asString()),
    attributes = attributes.asMap().map { (key, value) -> Attribute(key.key, value.toString()) },
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
)
