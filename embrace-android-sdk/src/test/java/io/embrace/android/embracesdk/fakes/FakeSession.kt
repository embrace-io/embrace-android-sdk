package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.Companion.APPLICATION_STATE_FOREGROUND
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.payload.getSessionSpan

internal fun fakeSessionZygote() = SessionZygote(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = APPLICATION_STATE_FOREGROUND,
    isColdStart = true,
    startType = Session.LifeEventType.STATE
)

internal fun fakeSessionMessage(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    endMs: Long = 161000400000L
): SessionMessage {
    val sessionSpan = Span(
        attributes = listOf(
            Attribute("emb.type", EmbType.Ux.Session.value)
        )
    )
    val session = Session(sessionId, startMs, endMs)
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

internal fun fakeSession(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L
): Session = Session(
    sessionId = sessionId,
    startTime = startMs
)

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
