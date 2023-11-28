package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    override fun startSession(
        coldStart: Boolean,
        startType: Session.SessionLifeEventType,
        startTime: Long
    ) {
        TODO("Not yet implemented")
    }

    override fun triggerStatelessSessionEnd(
        endType: Session.SessionLifeEventType,
        clearUserInfo: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun handleCrash(crashId: String) {
        TODO("Not yet implemented")
    }

    override fun endSessionManually(clearUserInfo: Boolean) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
