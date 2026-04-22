package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

/**
 * Returns the Session Span
 */
fun Envelope<SessionPartPayload>.findSessionSpan(): Span {
    return checkNotNull(getSessionSpan()) {
        "No session span found in session payload"
    }
}

/**
 * Return the user session ID from the session span in the payload
 */
fun Envelope<SessionPartPayload>.getSessionId(): String {
    return checkNotNull(findSessionSpan().attributes?.findAttributeValue("session.id")) {
        "No session id found in session payload"
    }
}

/**
 * Return the user session ID from the emb.user_session_id attribute in the session span.
 */
fun Envelope<SessionPartPayload>.getUserSessionId(): String {
    return checkNotNull(findSessionSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID)) {
        "No user session id found in session payload"
    }
}

/**
 * Return the session part ID from the session span in the payload. Unique per session part.
 */
fun Envelope<SessionPartPayload>.getSessionPartId(): String {
    return checkNotNull(findSessionSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID)) {
        "No session part id found in session payload"
    }
}

/**
 * Return the session start time in milliseconds from the session span in the payload
 */
fun Envelope<SessionPartPayload>.getStartTime(): Long {
    return checkNotNull(findSessionSpan().startTimeNanos?.nanosToMillis()) {
        "No start time found in session payload"
    }
}

/**
 * Return the last heartbeat time in milliseconds from the session span in the payload
 */
fun Envelope<SessionPartPayload>.getLastHeartbeatTimeMs(): Long {
    return checkNotNull(
        findSessionSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO)?.toLongOrNull()
            ?.nanosToMillis()
    ) {
        "No last heartbeat time found in session payload"
    }
}

/**
 * Finds the span matching the given [EmbType].
 */
fun Envelope<SessionPartPayload>.findSpanOfType(telemetryType: EmbType): Span {
    return findSpansOfType(telemetryType).single()
}

/**
 * Finds the span matching the given [EmbType].
 */
fun Envelope<SessionPartPayload>.findSpansOfType(telemetryType: EmbType): List<Span> {
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
fun Envelope<SessionPartPayload>.findSpansByName(name: String): List<Span> {
    val spans = checkNotNull(data.spans) {
        "No spans found in session message"
    }
    return checkNotNull(spans.filter { it.name == name }) {
        "Span not found named: $name"
    }
}

fun Envelope<SessionPartPayload>.findSpanByName(name: String): Span {
    return findSpansByName(name).single()
}

fun Envelope<SessionPartPayload>.findSpanSnapshotOfType(telemetryType: EmbType): Span {
    return findSpanSnapshotsOfType(telemetryType).single()
}

fun Envelope<SessionPartPayload>.findSpanSnapshotsOfType(telemetryType: EmbType): List<Span> {
    val snapshots = checkNotNull(data.spanSnapshots) {
        "No span snapshots found in session message"
    }
    return checkNotNull(snapshots.filter { it.hasEmbraceAttribute(telemetryType) }) {
        "Span snapshots of type not found: ${telemetryType.key}"
    }
}

fun Envelope<SessionPartPayload>.hasSpanSnapshotsOfType(telemetryType: EmbType): Boolean {
    return findSpanSnapshotsOfType(telemetryType).isNotEmpty()
}

fun Envelope<SessionPartPayload>.getNavigationStateSpan() = getStateSpan("emb-state-screen-automatic")
