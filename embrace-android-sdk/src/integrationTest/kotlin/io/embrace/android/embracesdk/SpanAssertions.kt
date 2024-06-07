package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.payload.SessionMessage

/**
 * Finds the first Span Event matching the given [TelemetryType]
 */
internal fun Span.findEventOfType(telemetryType: TelemetryType): SpanEvent {
    checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(events.single { it.hasFixedAttribute(telemetryType) }) {
        "Event not found: $name"
    }
}

/**
 * Finds the Span Events matching the given [TelemetryType]
 */
internal fun Span.findEventsOfType(telemetryType: TelemetryType): List<SpanEvent> {
    checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(events.filter { checkNotNull(it.attributes).hasFixedAttribute(telemetryType) }) {
        "Events not found: $name"
    }
}

/**
 * Returns true if an event exists with the given [TelemetryType]
 */
internal fun Span.hasEventOfType(telemetryType: TelemetryType): Boolean {
    checkNotNull(events) {
        "No events found in span"
    }
    return events.find { checkNotNull(it.attributes).hasFixedAttribute(telemetryType) } != null
}

/**
 * Returns the Session Span
 */
internal fun SessionMessage.findSessionSpan(): Span = findSpanOfType(EmbType.Ux.Session)

/**
 * Finds the span matching the given [TelemetryType].
 */
internal fun SessionMessage.findSpanOfType(telemetryType: TelemetryType): Span {
    return findSpansOfType(telemetryType).single()
}

/**
 * Finds the span matching the given [TelemetryType].
 */
internal fun SessionMessage.findSpansOfType(telemetryType: TelemetryType): List<Span> {
    val spans = checkNotNull(data?.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.hasFixedAttribute(telemetryType) }) {
        "Span of type not found: $telemetryType"
    }
}

/**
 * Finds the span matching the given [TelemetryType].
 */
internal fun SessionMessage.findSpansByName(name: String): List<Span> {
    val spans = checkNotNull(data?.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.name == name }) {
        "Span not found named: $name"
    }
}

/**
 * Returns true if a span exists with the given [TelemetryType].
 */
internal fun SessionMessage.hasSpanOfType(telemetryType: TelemetryType): Boolean {
    return findSpansOfType(telemetryType).isNotEmpty()
}

internal fun SessionMessage.findSpanSnapshotsOfType(telemetryType: TelemetryType): List<Span> {
    val snapshots = checkNotNull(data?.spanSnapshots) {
        "No span snapshots found in session message"
    }
    return checkNotNull(snapshots.filter { it.hasFixedAttribute(telemetryType) }) {
        "Span snapshots of type not found: ${telemetryType.key}"
    }
}

internal fun Map<String, String>.findAttributeValue(key: String): String? {
    return get(key)
}
