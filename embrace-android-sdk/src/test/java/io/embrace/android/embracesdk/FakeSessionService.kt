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

    override fun triggerStatelessSessionEnd(endType: Session.SessionLifeEventType) {
        TODO("Not yet implemented")
    }

    override fun handleCrash(crashId: String) {
        TODO("Not yet implemented")
    }

    override fun addProperty(key: String, value: String, permanent: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeProperty(key: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun getProperties(): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
