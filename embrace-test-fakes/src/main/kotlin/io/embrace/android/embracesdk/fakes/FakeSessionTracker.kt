package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.arch.state.AppState

class FakeSessionTracker : SessionTracker {
    var sessionData: SessionData? = null
    var listeners: MutableList<SessionChangeListener> = mutableListOf()

    override fun addListener(listener: SessionChangeListener) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionData? = sessionData

    override fun setActiveSession(sessionId: String?, appState: AppState) {
        sessionData = sessionId?.run { SessionData(sessionId, appState) }
        listeners.forEach(SessionChangeListener::onPostSessionChange)
    }
}
