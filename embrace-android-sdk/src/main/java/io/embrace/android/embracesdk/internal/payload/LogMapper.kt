package io.embrace.android.embracesdk.internal.payload

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.logs.data.LogRecordData

internal fun LogRecordData.toNewPayload(): Log = Log(
    traceId = spanContext.traceId,
    spanId = spanContext.spanId,
    timeUnixNano = observedTimestampEpochNanos,
    severityNumber = severity.severityNumber,
    severityText = severityText,
    body = LogBody(body.asString()),
    attributes = attributes.toNewPayload()
)

internal fun Attributes.toNewPayload(): List<Attribute> =
    this.asMap().entries.map { Attribute(it.key.key, it.value.toString()) }
