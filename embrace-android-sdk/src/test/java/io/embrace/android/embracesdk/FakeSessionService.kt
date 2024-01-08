package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    var crashId: String? = null

    override fun startSession(
        coldStart: Boolean,
        startType: Session.LifeEventType,
        startTime: Long
    ) {
        TODO("Not yet implemented")
    }

    override fun triggerStatelessSessionEnd(
        endType: Session.LifeEventType,
        clearUserInfo: Boolean
    ) {
        TODO("Not yet implemented")
    }

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun endSessionManually(clearUserInfo: Boolean) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
