package io.embrace.android.embracesdk.internal.logs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.opentelemetry.sdk.logs.data.LogRecordData

/**
 * Serializable representation of [EmbraceLogRecordData]
 */
@JsonClass(generateAdapter = true)
internal data class EmbraceLogRecordData(
    @Json(name = "trace_id")
    val traceId: String,

    @Json(name = "span_id")
    val spanId: String,

    @Json(name = "time_unix_nano")
    val timeUnixNanos: Long,

    @Json(name = "severity_number")
    val severityNumber: Int,

    @Json(name = "severity_text")
    val severityText: String?,

    @Json(name = "body")
    val body: EmbraceBody,

    @Json(name = "attributes")
    val attributes: List<Pair<String, Any>> = listOf()
) {

    internal constructor(logRecordData: LogRecordData) : this(
        traceId = logRecordData.spanContext.traceId,
        spanId = logRecordData.spanContext.spanId,
        timeUnixNanos = logRecordData.observedTimestampEpochNanos,
        severityNumber = logRecordData.severity.severityNumber,
        severityText = logRecordData.severityText,
        body = EmbraceBody(logRecordData.body.asString()),
        attributes = logRecordData.attributes.asMap().entries.associate { it.key.key to it.value }.toList()
    )
}
