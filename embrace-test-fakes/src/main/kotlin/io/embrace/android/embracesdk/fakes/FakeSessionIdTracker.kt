package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker

public class FakeSessionIdTracker : SessionIdTracker {

    public var sessionData: SessionData? = null

    override fun addListener(listener: (String?) -> Unit) {}

    override fun getActiveSession(): SessionData? = sessionData

    override fun setActiveSession(sessionId: String?, isSession: Boolean) {
        sessionData = sessionId?.run { SessionData(sessionId, isSession) }
    }
}
