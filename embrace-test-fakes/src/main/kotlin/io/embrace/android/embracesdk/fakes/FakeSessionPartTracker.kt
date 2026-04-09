package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker

class FakeSessionPartTracker : SessionPartTracker {
    var currentSession: SessionPartToken? = null
    var sessionChangeListeners: MutableList<SessionPartChangeListener> = mutableListOf()
    var sessionEndListeners: MutableList<SessionPartEndListener> = mutableListOf()

    override fun addSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionChangeListeners.add(listener)
    }

    override fun addSessionPartEndListener(listener: SessionPartEndListener) {
        sessionEndListeners.add(listener)
    }

    override fun getActiveSession(): SessionPartToken? = currentSession

    override fun newActiveSession(
        endSessionCallback: SessionPartToken.() -> Unit,
        startSessionCallback: () -> SessionPartToken?,
        postTransitionAppState: AppState,
    ): SessionPartToken? {
        currentSession = startSessionCallback()
        sessionChangeListeners.forEach(SessionPartChangeListener::onPostSessionChange)

        return currentSession
    }
}
