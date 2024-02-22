package io.embrace.android.embracesdk.internal.spans

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Serializable representation of [EmbraceSpanData]
 */
@JsonClass(generateAdapter = true)
internal data class EmbraceSpanData(
    @Json(name = "trace_id")
    val traceId: String,

    @Json(name = "span_id")
    val spanId: String,

    @Json(name = "parent_span_id")
    val parentSpanId: String?,

    @Json(name = "name")
    val name: String,

    @Json(name = "start_time_unix_nano")
    val startTimeNanos: Long,

    @Json(name = "end_time_unix_nano")
    val endTimeNanos: Long,

    @Json(name = "status")
    val status: StatusCode = StatusCode.UNSET,

    @Json(name = "events")
    val events: List<EmbraceSpanEvent> = emptyList(),

    @Json(name = "attributes")
    val attributes: Map<String, String> = emptyMap()
) {
    internal constructor(spanData: SpanData) : this(
        traceId = spanData.spanContext.traceId,
        spanId = spanData.spanContext.spanId,
        parentSpanId = spanData.parentSpanId,
        name = spanData.name,
        startTimeNanos = spanData.startEpochNanos,
        endTimeNanos = spanData.endEpochNanos,
        status = spanData.status.statusCode,
        events = fromEventData(eventDataList = spanData.events),
        attributes = spanData.attributes.asMap().entries.associate { it.key.key to it.value.toString() }
    )

    companion object {
        fun fromEventData(eventDataList: List<EventData>?): List<EmbraceSpanEvent> {
            val events = mutableListOf<EmbraceSpanEvent>()
            eventDataList?.forEach { eventData ->
                val event = EmbraceSpanEvent.create(
                    name = eventData.name,
                    timestampMs = eventData.epochNanos.nanosToMillis(),
                    attributes = eventData.attributes.asMap().entries.associate { it.key.key to it.value.toString() }
                )
                if (event != null) {
                    events.add(event)
                }
            }
            return events
        }
    }
}
