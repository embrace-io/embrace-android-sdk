package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan

/**
 * Returns the Session Span
 */
fun Envelope<SessionPayload>.findSessionSpan(): Span {
    return checkNotNull(getSessionSpan()) {
        "No session span found in session payload"
    }
}

/**
 * Return the session ID from the session span in the payload
 */
fun Envelope<SessionPayload>.getSessionId(): String {
    return checkNotNull(findSessionSpan().attributes?.findAttributeValue("session.id")) {
        "No session id found in session payload"
    }
}

/**
 * Return the session start time in milliseconds from the session span in the payload
 */
fun Envelope<SessionPayload>.getStartTime(): Long {
    return checkNotNull(findSessionSpan().startTimeNanos?.nanosToMillis()) {
        "No start time found in session payload"
    }
}

/**
 * Return the last heartbeat time in milliseconds from the session span in the payload
 */
fun Envelope<SessionPayload>.getLastHeartbeatTimeMs(): Long {
    return checkNotNull(
        findSessionSpan().attributes?.findAttributeValue(embHeartbeatTimeUnixNano.name)?.toLongOrNull()
            ?.nanosToMillis()
    ) {
        "No last heartbeat time found in session payload"
    }
}

/**
 * Finds the span matching the given [EmbType].
 */
fun Envelope<SessionPayload>.findSpanOfType(telemetryType: EmbType): Span {
    return findSpansOfType(telemetryType).single()
}

/**
 * Finds the span matching the given [EmbType].
 */
fun Envelope<SessionPayload>.findSpansOfType(telemetryType: EmbType): List<Span> {
    val spans = checkNotNull(data.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.hasEmbraceAttribute(telemetryType) }) {
        "Span of type not found: $telemetryType"
    }
}

/**
 * Finds the span matching the given [EmbType].
 */
fun Envelope<SessionPayload>.findSpansByName(name: String): List<Span> {
    val spans = checkNotNull(data.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.name == name }) {
        "Span not found named: $name"
    }
}

fun Envelope<SessionPayload>.findSpanByName(name: String): Span {
    return findSpansByName(name).single()
}

/**
 * Returns true if a span exists with the given [EmbType].
 */
fun Envelope<SessionPayload>.hasSpanOfType(telemetryType: EmbType): Boolean {
    return findSpansOfType(telemetryType).isNotEmpty()
}

fun Envelope<SessionPayload>.findSpanSnapshotOfType(telemetryType: EmbType): Span {
    return findSpanSnapshotsOfType(telemetryType).single()
}

fun Envelope<SessionPayload>.findSpanSnapshotsOfType(telemetryType: EmbType): List<Span> {
    val snapshots = checkNotNull(data.spanSnapshots) {
        "No span snapshots found in session message"
    }
    return checkNotNull(snapshots.filter { it.hasEmbraceAttribute(telemetryType) }) {
        "Span snapshots of type not found: ${telemetryType.key}"
    }
}

fun Envelope<SessionPayload>.hasSpanSnapshotsOfType(telemetryType: EmbType): Boolean {
    return findSpanSnapshotsOfType(telemetryType).isNotEmpty()
}
