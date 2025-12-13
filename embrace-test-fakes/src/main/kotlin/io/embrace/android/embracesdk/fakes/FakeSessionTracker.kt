package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionToken
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeSessionTracker : SessionTracker {
    var currentSession: SessionToken? = null
    var sessionChangeListeners: MutableList<SessionChangeListener> = mutableListOf()
    var sessionEndListeners: MutableList<SessionEndListener> = mutableListOf()

    override fun addSessionChangeListener(listener: SessionChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun addSessionEndListener(listener: SessionEndListener) {
        sessionEndListeners.add(listener)
    }

    override fun getActiveSession(): SessionToken? = currentSession

    override fun newActiveSession(
        endSessionCallback: SessionToken.() -> Unit,
        startSessionCallback: () -> SessionToken?,
        postTransitionAppState: AppState,
    ): SessionToken? {
        currentSession = startSessionCallback()
        sessionChangeListeners.forEach(SessionChangeListener::onPostSessionChange)

        return currentSession
    }
}
