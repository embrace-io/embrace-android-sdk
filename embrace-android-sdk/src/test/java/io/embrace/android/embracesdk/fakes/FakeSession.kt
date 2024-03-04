package io.embrace.android.embracesdk.fakes

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

internal fun fakeSessionMessage(): SessionMessage = SessionMessage(
    session = fakeSession()
)
