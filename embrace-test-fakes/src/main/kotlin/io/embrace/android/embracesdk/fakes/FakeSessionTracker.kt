package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionToken
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeSessionTracker : SessionTracker {
    var currentSession: SessionToken? = null
    var listeners: MutableList<SessionChangeListener> = mutableListOf()

    override fun addListener(listener: SessionChangeListener) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionToken? = currentSession

    override fun newActiveSession(
        endSessionCallback: SessionToken.() -> Unit,
        startSessionCallback: () -> SessionToken?,
        postTransitionAppState: AppState,
    ): SessionToken? {
        currentSession = startSessionCallback()
        listeners.forEach(SessionChangeListener::onPostSessionChange)

        return currentSession
    }
}
