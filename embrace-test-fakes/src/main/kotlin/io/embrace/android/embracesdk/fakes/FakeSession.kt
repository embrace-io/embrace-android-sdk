package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.opentelemetry.api.trace.SpanId

public fun fakeSessionZygote(): SessionZygote = SessionZygote(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = ApplicationState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE
)

public fun fakeSessionEnvelope(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L
): Envelope<SessionPayload> {
    val sessionSpan = Span(
        startTimeNanos = startMs.millisToNanos(),
        endTimeNanos = endMs.millisToNanos(),
        parentSpanId = SpanId.getInvalid(),
        attributes = listOf(
            Attribute("emb.type", EmbType.Ux.Session.value),
            Attribute("session.id", sessionId)
        )
    )
    val spans = listOf(testSpan, sessionSpan)
    val spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot())

    return Envelope(
        data = SessionPayload(
            spans = spans,
            spanSnapshots = spanSnapshots
        )
    )
}

public fun fakeIncompleteSessionEnvelope(
    sessionId: String = "fakeIncompleteSessionId",
    startMs: Long = 1691000000000L,
    lastHeartbeatTimeMs: Long = 1691000300000L,
): Envelope<SessionPayload> {
    val fakeClock = FakeClock(currentTime = startMs)
    val incompleteSessionSpan = FakePersistableEmbraceSpan.sessionSpan(
        sessionId = sessionId,
        startTimeMs = startMs,
        lastHeartbeatTimeMs = lastHeartbeatTimeMs
    )
    return Envelope(
        data = SessionPayload(
            spanSnapshots = listOfNotNull(
                incompleteSessionSpan.snapshot(),
                FakePersistableEmbraceSpan.started(clock = fakeClock).snapshot()
            )
        )
    )
}

public fun Envelope<SessionPayload>.mutateSessionSpan(action: (original: Span) -> Span): Envelope<SessionPayload> {
    val spans = data.spans
    val sessionSpan = checkNotNull(getSessionSpan())
    return copy(
        data = data.copy(
            spans?.minus(sessionSpan)?.plus(action(sessionSpan))
        )
    )
}
