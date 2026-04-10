package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.AppState

fun fakeSessionPartToken(): SessionPartToken = SessionPartToken(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = AppState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE
)

fun fakeSessionEnvelope(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L,
    sessionProperties: Map<String, String>? = null,
): Envelope<SessionPartPayload> {
    val sessionSpan = FakeEmbraceSdkSpan.sessionSpan(
        sessionId = sessionId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = endMs,
        endTimeMs = endMs,
        sessionProperties = sessionProperties,
    )
    val spans = listOf(testSpan, checkNotNull(sessionSpan.snapshot()))
    val spanSnapshots = listOfNotNull(FakeEmbraceSdkSpan.started().snapshot())

    return Envelope(
        resource = fakeEnvelopeResource,
        metadata = fakeEnvelopeMetadata,
        version = "1.0.0",
        type = "spans",
        data = SessionPartPayload(
            spans = spans,
            spanSnapshots = spanSnapshots
        )
    )
}

fun fakeIncompleteSessionEnvelope(
    sessionId: String = "fakeIncompleteSessionId",
    processIdentifier: String = "fakeIncompleteSessionProcessId",
    startMs: Long = 1691000000000L,
    lastHeartbeatTimeMs: Long = 1691000300000L,
    sessionProperties: Map<String, String>? = null,
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
): Envelope<SessionPartPayload> {
    val fakeClock = FakeClock(currentTime = startMs)
    val incompleteSessionSpan = FakeEmbraceSdkSpan.sessionSpan(
        sessionId = sessionId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = lastHeartbeatTimeMs,
        sessionProperties = sessionProperties,
        processIdentifier = processIdentifier
    )
    return Envelope(
        resource = resource,
        metadata = metadata,
        version = "1.0.0",
        type = "spans",
        data = SessionPartPayload(
            spanSnapshots = listOfNotNull(
                incompleteSessionSpan.snapshot(),
                FakeEmbraceSdkSpan.started(clock = fakeClock).snapshot()
            )
        )
    )
}
