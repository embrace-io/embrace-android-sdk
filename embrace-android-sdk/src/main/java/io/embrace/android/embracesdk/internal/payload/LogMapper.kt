package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordData

internal fun EmbraceLogRecordData.toNewPayload(): Log = Log(
    timeUnixNano = timeUnixNanos,
    severityNumber = severityNumber,
    severityText = severityText,
    body = LogBody(body.message),
    attributes = attributes.toNewPayload(),
    traceId = traceId,
    spanId = spanId,
)

internal fun Map<String, Any>.toNewPayload(): List<Attribute> =
    map { (key, value) -> Attribute(key, value.toString()) }
