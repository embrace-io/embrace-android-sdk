package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute

/**
 * Finds the first Span Event matching the given [TelemetryType]
 */
public fun Span.findEventOfType(telemetryType: TelemetryType): SpanEvent {
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
public fun Span.findEventsOfType(telemetryType: TelemetryType): List<SpanEvent> {
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
public fun Span.hasEventOfType(telemetryType: TelemetryType): Boolean {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return sanitizedEvents.find { checkNotNull(it.attributes).hasFixedAttribute(telemetryType) } != null
}

/**
 * Returns the Session Span
 */
public fun Envelope<SessionPayload>.findSessionSpan(): Span = findSpanOfType(EmbType.Ux.Session)

public fun Envelope<SessionPayload>.getSessionId(): String {
    val sessionSpan = checkNotNull(getSessionSpan()) {
        "No session span found in session message"
    }
    return checkNotNull(sessionSpan.attributes?.findAttributeValue("session.id")) {
        "No session id found in session message"
    }
}

public fun Envelope<SessionPayload>.getStartTime(): Long {
    val sessionSpan = checkNotNull(getSessionSpan()) {
        "No session span found in session message"
    }
    return checkNotNull(sessionSpan.startTimeNanos?.nanosToMillis()) {
        "No start time found in session message"
    }
}

/**
 * Finds the span matching the given [TelemetryType].
 */
public fun Envelope<SessionPayload>.findSpanOfType(telemetryType: TelemetryType): Span {
    return findSpansOfType(telemetryType).single()
}

/**
 * Finds the span matching the given [TelemetryType].
 */
public fun Envelope<SessionPayload>.findSpansOfType(telemetryType: TelemetryType): List<Span> {
    val spans = checkNotNull(data.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.hasFixedAttribute(telemetryType) }) {
        "Span of type not found: $telemetryType"
    }
}

/**
 * Finds the span matching the given [TelemetryType].
 */
public fun Envelope<SessionPayload>.findSpansByName(name: String): List<Span> {
    val spans = checkNotNull(data.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.name == name }) {
        "Span not found named: $name"
    }
}

/**
 * Returns true if a span exists with the given [TelemetryType].
 */
public fun Envelope<SessionPayload>.hasSpanOfType(telemetryType: TelemetryType): Boolean {
    return findSpansOfType(telemetryType).isNotEmpty()
}

public fun Envelope<SessionPayload>.findSpanSnapshotsOfType(telemetryType: TelemetryType): List<Span> {
    val snapshots = checkNotNull(data.spanSnapshots) {
        "No span snapshots found in session message"
    }
    return checkNotNull(snapshots.filter { it.hasFixedAttribute(telemetryType) }) {
        "Span snapshots of type not found: ${telemetryType.key}"
    }
}

public fun Map<String, String>.findAttributeValue(key: String): String? {
    return get(key)
}
