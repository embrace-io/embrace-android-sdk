package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeSessionTracker : SessionTracker {
    var currentSession: SessionPartToken? = null
    var sessionChangeListeners: MutableList<SessionChangeListener> = mutableListOf()
    var sessionEndListeners: MutableList<SessionEndListener> = mutableListOf()

    override fun addSessionChangeListener(listener: SessionChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun addSessionEndListener(listener: SessionEndListener) {
        sessionEndListeners.add(listener)
    }

    override fun getActiveSession(): SessionPartToken? = currentSession

    override fun newActiveSession(
        endSessionCallback: SessionPartToken.() -> Unit,
        startSessionCallback: () -> SessionPartToken?,
        postTransitionAppState: AppState,
    ): SessionPartToken? {
        currentSession = startSessionCallback()
        sessionChangeListeners.forEach(SessionChangeListener::onPostSessionChange)

        return currentSession
    }
}
