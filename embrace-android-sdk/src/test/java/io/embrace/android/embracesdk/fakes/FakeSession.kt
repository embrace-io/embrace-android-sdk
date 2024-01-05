package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.Companion.APPLICATION_STATE_FOREGROUND
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.MESSAGE_TYPE_START

internal fun fakeSession(): Session = Session(
    sessionId = "fakeSessionId",
    startTime = 160000000000L,
    number = 1,
    appState = APPLICATION_STATE_FOREGROUND,
    isColdStart = true,
    startType = Session.SessionLifeEventType.STATE,
    properties = mapOf(),
    messageType = MESSAGE_TYPE_START,
    user = null
)

internal fun fakeSessionMessage(): SessionMessage = SessionMessage(
    session = fakeSession()
)
