package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken

fun fakeSessionPartToken(): SessionPartToken = SessionPartToken(
    sessionPartId = "fakeSessionPartId",
    userSessionId = "fakeUserSessionId",
    startTime = 160000000000L,
    appState = AppState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE,
    userSessionPartIndex = 1,
    sessionPartNumber = 1,
)

fun fakeSessionEnvelope(
    userSessionId: String = "fakeUserSessionId",
    sessionPartId: String = "fakeSessionPartId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L,
    sessionProperties: Map<String, String>? = null,
): Envelope<SessionPartPayload> {
    val sessionPartSpan = FakeEmbraceSdkSpan.sessionPartSpan(
        userSessionId = userSessionId,
        sessionPartId = sessionPartId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = endMs,
        endTimeMs = endMs,
        sessionProperties = sessionProperties,
    )
    val spans = listOf(testSpan, checkNotNull(sessionPartSpan.snapshot()))
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
    userSessionId: String = "fakeIncompleteSessionId",
    sessionPartId: String = "fakeIncompleteSessionPartId",
    processIdentifier: String = "fakeIncompleteSessionProcessId",
    startMs: Long = 1691000000000L,
    lastHeartbeatTimeMs: Long = 1691000300000L,
    sessionProperties: Map<String, String>? = null,
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
): Envelope<SessionPartPayload> {
    val fakeClock = FakeClock(currentTime = startMs)
    val incompleteSessionPartSpan = FakeEmbraceSdkSpan.sessionPartSpan(
        userSessionId = userSessionId,
        sessionPartId = sessionPartId,
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
                incompleteSessionPartSpan.snapshot(),
                FakeEmbraceSdkSpan.started(clock = fakeClock).snapshot()
            )
        )
    )
}
