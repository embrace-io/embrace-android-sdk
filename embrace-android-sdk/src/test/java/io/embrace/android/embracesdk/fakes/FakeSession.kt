package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.payload.getSessionSpan

internal fun fakeSessionZygote() = SessionZygote(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = ApplicationState.FOREGROUND,
    isColdStart = true,
    startType = LifeEventType.STATE
)

internal fun fakeSessionMessage(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L
): SessionMessage {
    val sessionSpan = Span(
        startTimeNanos = startMs.millisToNanos(),
        attributes = listOf(
            Attribute("emb.type", EmbType.Ux.Session.value),
            Attribute(embSessionId.name, sessionId)
        )
    )
    val session = Session(endMs)
    val spans = listOf(testSpan, sessionSpan)
    val spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot())

    return SessionMessage(
        session = session,
        data = SessionPayload(
            spans = spans,
            spanSnapshots = spanSnapshots
        )
    )
}

internal fun SessionMessage.mutateSessionSpan(action: (original: Span) -> Span): SessionMessage {
    val spans = checkNotNull(data).spans
    val sessionSpan = checkNotNull(getSessionSpan())
    return copy(
        data = data.copy(
            spans?.minus(sessionSpan)?.plus(action(sessionSpan))
        )
    )
}

internal fun fakeCachedSessionMessageWithTerminationTime(): SessionMessage {
    val base = fakeSessionMessage(sessionId = "fakeSessionWithTerminationTime")
    return base.copy(
        session = base.session.copy(
            endTime = null,
            terminationTime = 161000500000L
        )
    )
}

internal fun fakeCachedSessionMessageWithHeartbeatTime(): SessionMessage {
    val base = fakeSessionMessage(sessionId = "fakeSessionWithHeartbeat")
    return base.copy(
        session = base.session.copy(
            endTime = null,
            lastHeartbeatTime = 161000500000L
        )
    )
}
