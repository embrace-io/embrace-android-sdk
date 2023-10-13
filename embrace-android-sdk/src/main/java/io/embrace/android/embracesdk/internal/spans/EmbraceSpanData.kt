package io.embrace.android.embracesdk.internal.spans

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Serializable representation of [EmbraceSpanData]
 */
internal data class EmbraceSpanData(
    @SerializedName("trace_id")
    val traceId: String,

    @SerializedName("span_id")
    val spanId: String,

    @SerializedName("parent_span_id")
    val parentSpanId: String?,

    @SerializedName("name")
    val name: String,

    @SerializedName("start_time_unix_nano")
    val startTimeNanos: Long,

    @SerializedName("end_time_unix_nano")
    val endTimeNanos: Long,

    @SerializedName("status")
    val status: StatusCode = StatusCode.UNSET,

    @SerializedName("events")
    val events: List<EmbraceSpanEvent> = emptyList(),

    @SerializedName("attributes")
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
                    timestampNanos = eventData.epochNanos,
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
