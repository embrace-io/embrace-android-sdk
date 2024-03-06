package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.SessionMessage

internal val testSessionMessage = SessionMessage(
    session = fakeSession(
        sessionId = "80DCEC19B24B434599B04C237970B785",
        startMs = 1681972471807L,
        properties = mapOf("nah" to "nah nah")
    )
)

internal val testSessionMessageOneMinuteLater =
    testSessionMessage.copy(
        session = testSessionMessage.session.copy(
            startTime = testSessionMessage.session.startTime + 60000L,
            properties = mapOf("nah" to "nah nah", "no" to "no no no no"),
        )
    )

internal val testSessionMessage2 = SessionMessage(
    session = fakeSession(
        sessionId = "03FC033631F346C48AF6E3D5B56F6AA3",
        startMs = testSessionMessageOneMinuteLater.session.startTime + 300000L,
        number = testSessionMessageOneMinuteLater.session.number + 1,
        properties = mapOf("bur" to "boo bur")
    )
)
