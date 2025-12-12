package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeSessionTracker : SessionTracker {
    var sessionData: SessionData? = null
    var listeners: MutableList<SessionChangeListener> = mutableListOf()

    override fun addListener(listener: SessionChangeListener) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionData? = sessionData

    override fun newActiveSession(
        endingSession: SessionZygote?,
        endSessionCallback: SessionZygote.() -> Unit,
        startSessionCallback: () -> SessionZygote?,
        appState: AppState,
    ): SessionZygote? {
        val newSession = startSessionCallback()
        sessionData = newSession?.run { SessionData(sessionId, appState) }
        listeners.forEach(SessionChangeListener::onPostSessionChange)

        return newSession
    }
}
