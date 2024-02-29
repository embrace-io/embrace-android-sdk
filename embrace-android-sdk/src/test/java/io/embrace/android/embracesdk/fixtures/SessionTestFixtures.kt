package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage

internal val testSessionMessage = SessionMessage(
    session = Session(
        sessionId = "fakefakefakefakeId",
        startTime = 1681972471807L,
        number = 1,
        appState = Session.APPLICATION_STATE_FOREGROUND,
        isColdStart = true,
        startType = Session.LifeEventType.BKGND_STATE,
        properties = mapOf("nah" to "nah nah"),
        messageType = Session.MESSAGE_TYPE_END
    )
)

internal val testSessionMessageOneMinuteLater =
    testSessionMessage.copy(
        session = testSessionMessage.session.copy(
            startTime = 1681972531807L,
            number = 2,
            properties = mapOf("nah" to "nah nah", "no" to "no no no no"),
        )
    )
