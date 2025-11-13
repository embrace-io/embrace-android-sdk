package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState

class FakeSessionIdTracker : SessionIdTracker {
    var sessionData: SessionData? = null
    var listeners: MutableList<(String?) -> Unit> = mutableListOf()

    override fun addListener(listener: (String?) -> Unit) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionData? = sessionData

    override fun setActiveSession(sessionId: String?, appState: AppState) {
        sessionData = sessionId?.run { SessionData(sessionId, appState) }
        listeners.forEach {
            it(sessionId)
        }
    }
}
