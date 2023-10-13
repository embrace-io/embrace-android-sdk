package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.Session

internal fun fakeSession(): Session = Session.buildStartSession(
    "fakeSessionId",
    true,
    Session.SessionLifeEventType.STATE,
    160000000000L,
    1,
    null,
    mapOf()
)
