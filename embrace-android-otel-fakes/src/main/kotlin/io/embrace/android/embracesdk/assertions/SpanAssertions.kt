package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.payload.Link
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
    return checkNotNull(sanitizedEvents.filter { checkNotNull(it.attributes).hasEmbraceAttributeKey(telemetryType.key) }) {
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
    return sanitizedEvents.find { checkNotNull(it.attributes).hasEmbraceAttributeKey(telemetryType.key) } != null
}

fun Span.assertPreviousSession(previousSessionSpan: Span, previousSessionId: String) {
    findLinkOfType(LinkType.PreviousSession).validatePreviousSessionLink(previousSessionSpan, previousSessionId)
}

fun Span.assertNoPreviousSession() =
    links?.filter { it.attributes?.toMap()?.containsKey(LinkType.PreviousSession.key.name) == false }?.size == 0

fun Span.findLinksOfType(type: LinkType) = links?.filter { it.attributes?.hasEmbraceAttribute(type) == true }

fun Span.findLinkOfType(type: LinkType): Link = checkNotNull(findLinksOfType(type)?.single())

fun Span.findCustomLinks() = links?.filter { it.attributes?.any { attr -> attr.key == "emb.link_type" } == false }

fun Span.hasLinkToEmbraceSpan(linkedSpan: Span, type: LinkType): Boolean =
    findLinksOfType(type)?.any { it.isLinkedToSpan(linkedSpan, false) } == true
