package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.payload.SessionZygote

internal fun fakeSessionZygote() = SessionZygote(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = ApplicationState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE
)

internal fun fakeSessionEnvelope(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L
): Envelope<SessionPayload> {
    val sessionSpan = Span(
        startTimeNanos = startMs.millisToNanos(),
        endTimeNanos = endMs.millisToNanos(),
        attributes = listOf(
            Attribute("emb.type", EmbType.Ux.Session.value),
            Attribute(embSessionId.name, sessionId)
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

internal fun Envelope<SessionPayload>.mutateSessionSpan(action: (original: Span) -> Span): Envelope<SessionPayload> {
    val spans = data.spans
    val sessionSpan = checkNotNull(getSessionSpan())
    return copy(
        data = data.copy(
            spans?.minus(sessionSpan)?.plus(action(sessionSpan))
        )
    )
}

internal fun fakeCachedSessionEnvelopeWithTerminationTime(): Envelope<SessionPayload> {
    return fakeSessionEnvelope(sessionId = "fakeSessionWithTerminationTime")
}

internal fun fakeCachedSessionEnvelopeWithHeartbeatTime(): Envelope<SessionPayload> {
    return fakeSessionEnvelope(sessionId = "fakeSessionWithHeartbeat")
}
