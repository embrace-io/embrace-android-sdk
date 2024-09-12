package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

class FakeSessionIdTracker : SessionIdTracker {
    var sessionData: SessionData? = null
    var listeners: MutableList<(String?) -> Unit> = mutableListOf()

    override fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionData? = sessionData

    override fun setActiveSession(sessionId: String?, isSession: Boolean) {
        sessionData = sessionId?.run { SessionData(sessionId, isSession) }
    }
}
