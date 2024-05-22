package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

/**
 * Finds the first Span Event matching the given [TelemetryType]
 */
internal fun EmbraceSpanData.findEventOfType(telemetryType: TelemetryType): EmbraceSpanEvent =
    checkNotNull(events.single { it.attributes.hasFixedAttribute(telemetryType) }) {
        "Event not found: $name"
    }

/**
 * Finds the Span Events matching the given [TelemetryType]

 */
internal fun EmbraceSpanData.findEventsOfType(telemetryType: TelemetryType): List<EmbraceSpanEvent> =
    checkNotNull(events.filter { it.attributes.hasFixedAttribute(telemetryType) }) {
        "Events not found: $name"
    }

/**
 * Finds the span attribute matching the name.
 */
internal fun EmbraceSpanData.findSpanAttribute(key: String): String =
    checkNotNull(attributes[key]) {
        "Attribute not found: $key"
    }

/**
 * Finds the event attribute matching the name.
 */
internal fun EmbraceSpanEvent.findEventAttribute(key: String): String =
    checkNotNull(attributes[key]) {
        "Attribute not found: $key"
    }

/**
 * Finds the event attribute matching the name.
 */
internal fun Log.findLogAttribute(key: String): String =
    attributes?.single { it.key == key }?.data
        ?: throw IllegalArgumentException("Attribute not found: $key")

/**
 * Returns true if an event exists with the given [TelemetryType]
 */
internal fun EmbraceSpanData.hasEventOfType(telemetryType: TelemetryType): Boolean =
    events.find { it.attributes.hasFixedAttribute(telemetryType) } != null

/**
 * Returns the Session Span
 */
internal fun SessionMessage.findSessionSpan(): EmbraceSpanData = findSpanOfType(EmbType.Ux.Session)

/**
 * Finds the span matching the given [TelemetryType].
 */
internal fun SessionMessage.findSpanOfType(telemetryType: TelemetryType): EmbraceSpanData =
    checkNotNull(spans?.single { it.hasFixedAttribute(telemetryType) }) {
        "Span of type not found: ${telemetryType.key}"
    }

/**
 * Finds the span matching the given [TelemetryType].
 */
internal fun SessionMessage.findSpansOfType(telemetryType: TelemetryType): List<EmbraceSpanData> =
    checkNotNull(spans?.filter { it.hasFixedAttribute(telemetryType) }) {
        "Spans of type not found: ${telemetryType.key}"
    }

internal fun SessionMessage.findSpanSnapshotsOfType(telemetryType: TelemetryType): List<EmbraceSpanData> =
    checkNotNull(spanSnapshots?.filter { it.hasFixedAttribute(telemetryType) }) {
        "Span snapshots of type not found: ${telemetryType.key}"
    }

/**
 * Returns true if a span exists with the given [TelemetryType].
 */
internal fun SessionMessage.hasSpanOfType(telemetryType: TelemetryType): Boolean {
    return spans?.find { it.attributes.hasFixedAttribute(telemetryType) } != null
}
