package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.Companion.APPLICATION_STATE_FOREGROUND
import io.embrace.android.embracesdk.payload.SessionMessage

internal fun fakeSession(
    sessionId: String = "fakeSessionId",
    startMs: Long = 160000000000L,
    number: Int = 1,
    properties: Map<String, String> = mapOf()
): Session = Session(
    sessionId = sessionId,
    startTime = startMs,
    number = number,
    appState = APPLICATION_STATE_FOREGROUND,
    isColdStart = true,
    startType = Session.LifeEventType.STATE,
    properties = properties,
    messageType = Session.MESSAGE_TYPE_END
)

internal fun fakeV1SessionMessage(session: Session = fakeSession()): SessionMessage = SessionMessage(
    session = session
)

internal fun fakeEndedSessionMessage(
    session: Session = fakeSession(),
    spans: List<Span> = listOfNotNull(testSpan),
    spanSnapshots: List<Span> = listOfNotNull(),
): SessionMessage = SessionMessage(
    session = session.copy(endTime = 160000500000L),
    data = SessionPayload(
        spans = spans,
        spanSnapshots = spanSnapshots
    )
)

internal fun fakeEndedSessionMessageWithSnapshot(): SessionMessage = SessionMessage(
    session = fakeSession().copy(
        sessionId = "fakeSessionWithSnapshot",
        startTime = 161000000000L,
        endTime = 161000400000L
    ),
    data = SessionPayload(
        spans = listOfNotNull(testSpan),
        spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot())
    )
)

internal fun fakeCachedSessionMessageWithTerminationTime(): SessionMessage = SessionMessage(
    session = fakeSession().copy(
        sessionId = "fakeSessionWithTerminationTime",
        startTime = 161000000000L,
        terminationTime = 161000500000L
    ),
    data = SessionPayload(
        spans = listOfNotNull(testSpan),
        spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot()),
    )
)

internal fun fakeCachedSessionMessageWithHeartbeatTime(): SessionMessage = SessionMessage(
    session = fakeSession().copy(
        sessionId = "fakeSessionWithHeartbeat",
        startTime = 161000000000L,
        lastHeartbeatTime = 161000600000L
    ),
    data = SessionPayload(
        spans = listOfNotNull(testSpan),
        spanSnapshots = listOfNotNull(FakePersistableEmbraceSpan.started().snapshot()),
    )
)

internal fun fakeV2SessionMessage(): SessionMessage = SessionMessage(
    session = fakeSession(),
    metadata = EnvelopeMetadata(),
    resource = EnvelopeResource(),
    data = SessionPayload()
)
