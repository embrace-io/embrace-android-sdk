package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent

/**
 * Finds the first Span Event matching the given [EmbType]
 */
fun Span.findEventOfType(telemetryType: EmbType): SpanEvent {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.single { it.hasEmbraceAttribute(telemetryType) }) {
        "Event not found: $name"
    }
}

/**
 * Finds the Span Events matching the given [EmbType]
 */
fun Span.findEventsOfType(telemetryType: EmbType): List<SpanEvent> {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.filter { checkNotNull(it.attributes).hasEmbraceAttribute(telemetryType) }) {
        "Events not found: $name"
    }
}

/**
 * Returns true if an event exists with the given [EmbType]
 */
fun Span.hasEventOfType(telemetryType: EmbType): Boolean {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return sanitizedEvents.find { checkNotNull(it.attributes).hasEmbraceAttribute(telemetryType) } != null
}

fun Span.assertPreviousSession(previousSessionSpan: Span, previousSessionId: String) {
    val prevSessionLink = checkNotNull(links).single {
        it.attributes?.toMap()?.containsKey(LinkType.PreviousSession.key.name) == true
    }
    prevSessionLink.validatePreviousSessionLink(previousSessionSpan, previousSessionId)
}

fun Span.assertNoPreviousSession() =
    links?.filter { it.attributes?.toMap()?.containsKey(LinkType.PreviousSession.key.name) == false }?.size == 0
