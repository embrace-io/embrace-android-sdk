package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.getStartTime

internal val testSessionMessage = fakeSessionMessage(
    sessionId = "80DCEC19B24B434599B04C237970B785",
    startMs = 1681972471807L
)

internal val testSessionMessageOneMinuteLater = fakeSessionMessage(
    startMs = testSessionMessage.getStartTime() + 60000L
)

internal val testSessionMessage2 = fakeSessionMessage(
    sessionId = "03FC033631F346C48AF6E3D5B56F6AA3",
    startMs = testSessionMessageOneMinuteLater.getStartTime() + 300000L
)
