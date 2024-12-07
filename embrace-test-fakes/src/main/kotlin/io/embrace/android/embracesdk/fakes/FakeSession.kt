package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote

fun fakeSessionZygote(): SessionZygote = SessionZygote(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = ApplicationState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE
)

fun fakeSessionEnvelope(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L,
    sessionProperties: Map<String, String>? = null,
): Envelope<SessionPayload> {
    val sessionSpan = FakePersistableEmbraceSpan.sessionSpan(
        sessionId = sessionId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = endMs,
        endTimeMs = endMs,
        sessionProperties = sessionProperties,
    )
    val spans = listOf(testSpan, checkNotNull(sessionSpan.snapshot()))
    val spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot())

    return Envelope(
        resource = EnvelopeResource(),
        metadata = EnvelopeMetadata(),
        version = "1.0.0",
        type = "spans",
        data = SessionPayload(
            spans = spans,
            spanSnapshots = spanSnapshots
        )
    )
}

fun fakeIncompleteSessionEnvelope(
    sessionId: String = "fakeIncompleteSessionId",
    startMs: Long = 1691000000000L,
    lastHeartbeatTimeMs: Long = 1691000300000L,
    sessionProperties: Map<String, String>? = null,
): Envelope<SessionPayload> {
    val fakeClock = FakeClock(currentTime = startMs)
    val incompleteSessionSpan = FakePersistableEmbraceSpan.sessionSpan(
        sessionId = sessionId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = lastHeartbeatTimeMs,
        sessionProperties = sessionProperties,
    )
    return Envelope(
        resource = EnvelopeResource(),
        metadata = EnvelopeMetadata(),
        version = "1.0.0",
        type = "spans",
        data = SessionPayload(
            spanSnapshots = listOfNotNull(
                incompleteSessionSpan.snapshot(),
                FakePersistableEmbraceSpan.started(clock = fakeClock).snapshot()
            )
        )
    )
}
