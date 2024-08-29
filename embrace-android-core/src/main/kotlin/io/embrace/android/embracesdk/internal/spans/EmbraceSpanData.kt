package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.EventData

/**
 * Serializable representation of [EmbraceSpanData]
 */
public data class EmbraceSpanData(
    val traceId: String,

    val spanId: String,

    val parentSpanId: String?,

    val name: String,

    val startTimeNanos: Long,

    val endTimeNanos: Long,

    val status: StatusCode = StatusCode.UNSET,

    val events: List<EmbraceSpanEvent> = emptyList(),

    val attributes: Map<String, String> = emptyMap()
) {

    public companion object {
        public fun fromEventData(eventDataList: List<EventData>?): List<EmbraceSpanEvent> {
            val events = mutableListOf<EmbraceSpanEvent>()
            eventDataList?.forEach { eventData ->
                val event = EmbraceSpanEvent.create(
                    name = eventData.name,
                    timestampMs = eventData.epochNanos.nanosToMillis(),
                    attributes = eventData.attributes.toStringMap(),
                )
                if (event != null) {
                    events.add(event)
                }
            }
            return events
        }
    }
}
