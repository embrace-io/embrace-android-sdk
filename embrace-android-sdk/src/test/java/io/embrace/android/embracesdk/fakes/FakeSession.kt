package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.Companion.APPLICATION_STATE_FOREGROUND
import io.embrace.android.embracesdk.payload.SessionMessage

internal fun fakeSession(
    sessionId: String = "fakeSessionId",
): Session = Session(
    sessionId = sessionId,
    startTime = 160000000000L,
    number = 1,
    appState = APPLICATION_STATE_FOREGROUND,
    isColdStart = true,
    startType = Session.LifeEventType.STATE,
    properties = mapOf(),
    messageType = Session.MESSAGE_TYPE_END,
    user = null
)

internal fun fakeSessionMessage(): SessionMessage = SessionMessage(
    session = fakeSession()
)
