package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeSessionTracker : SessionTracker {
    var currentSession: SessionZygote? = null
    var listeners: MutableList<SessionChangeListener> = mutableListOf()

    override fun addListener(listener: SessionChangeListener) {
        listeners.add(listener)
    }

    override fun getActiveSession(): SessionZygote? = currentSession

    override fun newActiveSession(
        endSessionCallback: SessionZygote.() -> Unit,
        startSessionCallback: () -> SessionZygote?,
        postTransitionAppState: AppState,
    ): SessionZygote? {
        currentSession = startSessionCallback()
        listeners.forEach(SessionChangeListener::onPostSessionChange)

        return currentSession
    }
}
