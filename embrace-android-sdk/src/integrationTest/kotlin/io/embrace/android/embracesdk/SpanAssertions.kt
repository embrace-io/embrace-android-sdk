package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Finds the span event matching the name.
 */
internal fun Span.findEvent(name: String): SpanEvent =
    checkNotNull(events?.single { it.name == name }) {
        "Event not found: $name"
    }

/**
 * Finds the span attribute matching the name.
 */
internal fun Span.findSpanAttribute(key: String): String =
    checkNotNull(attributes?.single { it.key == key }?.data) {
        "Attribute not found: $key"
    }

/**
 * Finds the event attribute matching the name.
 */
internal fun SpanEvent.findEventAttribute(key: String): String =
    checkNotNull(attributes?.single { it.key == key }?.data) {
        "Attribute not found: $key"
    }

/**
 * Finds the emb-session span.
 */
internal fun SessionMessage.findSessionSpan(): Span = findSpan("emb-session")

/**
 * Finds the span matching the name.
 */
internal fun SessionMessage.findSpan(name: String): Span =
    checkNotNull(data?.spans?.single { it.name == name }) {
        "Span not found: $name"
    }

/**
 * Returns true if a span exists with the given name.
 */
internal fun SessionMessage.hasSpan(name: String): Boolean {
    return data?.spans?.singleOrNull { it.name == name } != null
}

/**
 * Returns true if an event exists with the given name.
 */
internal fun EmbraceSpanData.hasEvent(name: String): Boolean {
    return events.singleOrNull { it.name == name } != null
}