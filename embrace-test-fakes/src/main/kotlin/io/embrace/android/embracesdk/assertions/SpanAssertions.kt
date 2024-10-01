package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute

/**
 * Finds the first Span Event matching the given [TelemetryType]
 */
fun Span.findEventOfType(telemetryType: TelemetryType): SpanEvent {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.single { it.hasFixedAttribute(telemetryType) }) {
        "Event not found: $name"
    }
}

/**
 * Finds the Span Events matching the given [TelemetryType]
 */
fun Span.findEventsOfType(telemetryType: TelemetryType): List<SpanEvent> {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.filter { checkNotNull(it.attributes).hasFixedAttribute(telemetryType) }) {
        "Events not found: $name"
    }
}

/**
 * Returns true if an event exists with the given [TelemetryType]
 */
fun Span.hasEventOfType(telemetryType: TelemetryType): Boolean {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return sanitizedEvents.find { checkNotNull(it.attributes).hasFixedAttribute(telemetryType) } != null
}
