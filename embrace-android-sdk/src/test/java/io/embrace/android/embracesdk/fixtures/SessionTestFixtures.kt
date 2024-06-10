package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.fakeSessionMessage

internal val testSessionMessage = fakeSessionMessage(
    sessionId = "80DCEC19B24B434599B04C237970B785",
    startMs = 1681972471807L
).copy(data = null)

internal val testSessionMessageOneMinuteLater = fakeSessionMessage(
    startMs = testSessionMessage.session.startTime + 60000L
).copy(data = null)

internal val testSessionMessage2 = fakeSessionMessage(
    sessionId = "03FC033631F346C48AF6E3D5B56F6AA3",
    startMs = testSessionMessageOneMinuteLater.session.startTime + 300000L
).copy(data = null)
