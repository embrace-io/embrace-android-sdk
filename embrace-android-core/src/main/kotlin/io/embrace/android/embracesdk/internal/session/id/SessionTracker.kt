package io.embrace.android.embracesdk.internal.session.id

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.SessionZygote

interface SessionTracker {

    /**
     * Gets the currently active session, if present.
     */
    fun getActiveSession(): SessionData?

    /**
     * Gets the currently active session ID, if present.
     */
    fun getActiveSessionId(): String? = getActiveSession()?.id

    /**
     * Manage the transition of the current session
     */
    fun newActiveSession(
        endingSession: SessionZygote?,
        endSessionCallback: SessionZygote.() -> Unit,
        startSessionCallback: () -> SessionZygote?,
        appState: AppState
    ): SessionZygote?

    /**
     * Adds a listener that will be called when the session ID changes, and with the initial session
     * ID (if there is any).
     */
    fun addListener(listener: SessionChangeListener)
}
